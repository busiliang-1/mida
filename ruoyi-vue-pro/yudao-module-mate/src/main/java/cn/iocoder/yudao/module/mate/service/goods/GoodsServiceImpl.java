package cn.iocoder.yudao.module.mate.service.goods;

import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.module.mate.dal.dataobject.goods.GoodsItemDO;
import cn.iocoder.yudao.module.mate.dal.dataobject.goods.GoodsOrderDO;
import cn.iocoder.yudao.module.mate.dal.mysql.goods.GoodsItemMapper;
import cn.iocoder.yudao.module.mate.dal.mysql.goods.GoodsOrderMapper;
import cn.iocoder.yudao.module.mate.enums.goods.GoodsOrderStatusEnum;
import cn.iocoder.yudao.module.mate.enums.goods.GoodsStatusEnum;
import cn.iocoder.yudao.module.mate.event.GoodsSoldEvent;
import cn.iocoder.yudao.module.mate.framework.web.CursorPage;
import cn.iocoder.yudao.module.mate.service.auth.AuthService;
import cn.iocoder.yudao.module.mate.service.risk.SensitiveWordService;
import jakarta.annotation.Resource;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.mate.enums.ErrorCodeConstants.AUTH_LEVEL_INSUFFICIENT;
import static cn.iocoder.yudao.module.mate.enums.ErrorCodeConstants.CONTENT_AUDIT_HIT;
import static cn.iocoder.yudao.module.mate.enums.ErrorCodeConstants.PARAM_INVALID;
import static cn.iocoder.yudao.module.mate.enums.ErrorCodeConstants.RESOURCE_OWNER_MISMATCH;
import static cn.iocoder.yudao.module.mate.enums.ErrorCodeConstants.RESOURCE_STATUS_INVALID;

/**
 * M7 二手实现。
 * 并发控制：下单先读 version，再用 lock 的 WHERE status=0 AND version=? 抢占；
 * 影响 0 行说明商品已被别人锁定（不是余额/网络问题），按"已售出"返回 2002，不重试、不覆盖。
 */
@Service
@Validated
public class GoodsServiceImpl implements GoodsService {

    private static final int MAX_PAGE_SIZE = 50;
    private static final int TITLE_MAX = 60;
    private static final int CATEGORY_MAX = 16;
    private static final int DESC_MAX = 1000;

    @Resource
    private GoodsItemMapper goodsItemMapper;
    @Resource
    private GoodsOrderMapper goodsOrderMapper;
    @Resource
    private AuthService authService;
    @Resource
    private SensitiveWordService sensitiveWordService;
    @Resource
    private ApplicationEventPublisher eventPublisher;

    @Override
    public Long publish(Long sellerId, PublishCmd cmd) {
        // 挂售需 L3；服务层再守一道，防止内部调用绕过 Controller 切面
        assertLevel(sellerId, 3);
        validatePublish(cmd);
        assertContentPass(cmd.title());
        assertContentPass(cmd.description());

        GoodsItemDO goods = GoodsItemDO.builder()
                .sellerId(sellerId)
                .title(cmd.title())
                .category(cmd.category())
                .price(cmd.price().setScale(2, RoundingMode.HALF_UP))
                .description(cmd.description())
                .images(cmd.images())
                .status(GoodsStatusEnum.ON_SALE.getStatus())
                .version(0)
                .build();
        goodsItemMapper.insert(goods);
        return goods.getGoodsId();
    }

    @Override
    public CursorPage<GoodsItem> list(String category, String cursor, int size) {
        int pageSize = size <= 0 ? 20 : Math.min(size, MAX_PAGE_SIZE);
        Long beforeId = parseCursor(cursor);
        List<GoodsItemDO> rows = new ArrayList<>(goodsItemMapper.selectOnSale(category, beforeId, pageSize + 1));
        boolean hasMore = rows.size() > pageSize;
        if (hasMore) {
            rows = new ArrayList<>(rows.subList(0, pageSize));
        }
        String nextCursor = hasMore ? String.valueOf(rows.get(rows.size() - 1).getGoodsId()) : null;
        return new CursorPage<>(rows.stream().map(this::toItem).toList(), nextCursor, hasMore);
    }

    @Override
    public GoodsItem get(Long goodsId) {
        GoodsItemDO goods = goodsItemMapper.selectById(goodsId);
        if (goods == null) {
            throw exception(RESOURCE_OWNER_MISMATCH);
        }
        return toItem(goods);
    }

    @Override
    public Integer offShelf(Long sellerId, Long goodsId) {
        GoodsItemDO goods = goodsItemMapper.selectById(goodsId);
        if (goods == null || !Objects.equals(goods.getSellerId(), sellerId)) {
            throw exception(RESOURCE_OWNER_MISMATCH);
        }
        if (Objects.equals(goods.getStatus(), GoodsStatusEnum.OFF.getStatus())) {
            return GoodsStatusEnum.OFF.getStatus(); // 幂等
        }
        if (!GoodsStatusEnum.canTransition(goods.getStatus(), GoodsStatusEnum.OFF.getStatus())) {
            throw exception(RESOURCE_STATUS_INVALID);
        }
        if (goodsItemMapper.offShelf(goodsId, sellerId) == 0) {
            throw exception(RESOURCE_STATUS_INVALID);
        }
        return GoodsStatusEnum.OFF.getStatus();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrderResult createOrder(Long buyerId, Long goodsId, String idempotencyKey) {
        if (StrUtil.isBlank(idempotencyKey)) {
            throw exception(PARAM_INVALID);
        }
        assertLevel(buyerId, 2);
        GoodsItemDO goods = goodsItemMapper.selectById(goodsId);
        if (goods == null) {
            throw exception(RESOURCE_OWNER_MISMATCH);
        }
        if (Objects.equals(goods.getSellerId(), buyerId)) {
            throw exception(RESOURCE_OWNER_MISMATCH);
        }
        String orderNo = buildOrderNo(buyerId, idempotencyKey);
        GoodsOrderDO existing = goodsOrderMapper.selectByNo(orderNo);
        if (existing != null) {
            return new OrderResult(existing.getOrderNo(), existing.getStatus());
        }
        if (!Objects.equals(goods.getStatus(), GoodsStatusEnum.ON_SALE.getStatus())) {
            throw exception(RESOURCE_STATUS_INVALID);
        }
        if (goodsOrderMapper.countActiveByBuyer(buyerId) > 0) {
            throw exception(RESOURCE_STATUS_INVALID);
        }
        // 乐观锁抢货：0 行即已被别人买走
        if (goodsItemMapper.lock(goodsId, goods.getVersion()) == 0) {
            throw exception(RESOURCE_STATUS_INVALID);
        }
        GoodsOrderDO order = GoodsOrderDO.builder()
                .orderNo(orderNo)
                .goodsId(goodsId)
                .buyerId(buyerId)
                .sellerId(goods.getSellerId())
                .amount(goods.getPrice())
                .status(GoodsOrderStatusEnum.CREATED.getStatus())
                .build();
        try {
            goodsOrderMapper.insert(order);
        } catch (DuplicateKeyException ex) {
            GoodsOrderDO duplicated = goodsOrderMapper.selectByNo(orderNo);
            return new OrderResult(duplicated.getOrderNo(), duplicated.getStatus());
        }
        return new OrderResult(order.getOrderNo(), order.getStatus());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Integer confirm(Long userId, Long orderId) {
        GoodsOrderDO order = goodsOrderMapper.selectById(orderId);
        if (order == null) {
            throw exception(RESOURCE_OWNER_MISMATCH);
        }
        boolean participant = Objects.equals(order.getBuyerId(), userId)
                || Objects.equals(order.getSellerId(), userId);
        if (!participant) {
            throw exception(RESOURCE_OWNER_MISMATCH);
        }
        if (Objects.equals(order.getStatus(), GoodsOrderStatusEnum.COMPLETED.getStatus())) {
            return GoodsOrderStatusEnum.COMPLETED.getStatus(); // 幂等
        }
        if (!GoodsOrderStatusEnum.canTransition(order.getStatus(), GoodsOrderStatusEnum.COMPLETED.getStatus())) {
            throw exception(RESOURCE_STATUS_INVALID);
        }
        if (goodsOrderMapper.complete(orderId) == 0) {
            throw exception(RESOURCE_STATUS_INVALID);
        }
        goodsItemMapper.markSold(order.getGoodsId());
        // 无资金托管：只发事件供信用分统计，绝不触发钱包扣款/入账
        eventPublisher.publishEvent(new GoodsSoldEvent(order.getGoodsId(), orderId,
                order.getSellerId(), order.getBuyerId(), order.getAmount()));
        return GoodsOrderStatusEnum.COMPLETED.getStatus();
    }

    @Override
    public CursorPage<OrderItem> myOrders(Long userId, Integer role, String cursor, int size) {
        int pageSize = size <= 0 ? 20 : Math.min(size, MAX_PAGE_SIZE);
        Long beforeId = parseCursor(cursor);
        List<GoodsOrderDO> rows = new ArrayList<>(
                goodsOrderMapper.selectMyOrders(userId, role, beforeId, pageSize + 1));
        boolean hasMore = rows.size() > pageSize;
        if (hasMore) {
            rows = new ArrayList<>(rows.subList(0, pageSize));
        }
        String nextCursor = hasMore ? String.valueOf(rows.get(rows.size() - 1).getOrderId()) : null;
        return new CursorPage<>(rows.stream()
                .map(o -> new OrderItem(o.getOrderId(), o.getOrderNo(), o.getGoodsId(), o.getBuyerId(),
                        o.getSellerId(), o.getAmount(), o.getStatus(), o.getCreateTime()))
                .toList(), nextCursor, hasMore);
    }

    // ========== 内部方法 ==========

    private void assertLevel(Long userId, int required) {
        Integer level = authService.getAuthLevel(userId);
        if (level == null || level < required) {
            throw exception(AUTH_LEVEL_INSUFFICIENT);
        }
    }

    private void validatePublish(PublishCmd cmd) {
        if (cmd == null || StrUtil.isBlank(cmd.title()) || cmd.title().length() > TITLE_MAX
                || StrUtil.isBlank(cmd.category()) || cmd.category().length() > CATEGORY_MAX
                || cmd.price() == null || cmd.price().signum() <= 0
                || cmd.price().stripTrailingZeros().scale() > 2
                || cmd.images() == null || cmd.images().isEmpty()
                || (cmd.description() != null && cmd.description().length() > DESC_MAX)) {
            throw exception(PARAM_INVALID);
        }
    }

    private void assertContentPass(String text) {
        if (SensitiveWordService.AuditResult.HIT.equals(sensitiveWordService.match(text).verdict())) {
            throw exception(CONTENT_AUDIT_HIT);
        }
    }

    private String buildOrderNo(Long buyerId, String idempotencyKey) {
        return "G" + DigestUtil.sha256Hex(buyerId + ":" + idempotencyKey).substring(0, 24).toUpperCase();
    }

    private Long parseCursor(String cursor) {
        if (StrUtil.isBlank(cursor)) {
            return null;
        }
        try {
            return Long.valueOf(cursor);
        } catch (NumberFormatException ex) {
            throw exception(PARAM_INVALID);
        }
    }

    private GoodsItem toItem(GoodsItemDO goods) {
        return new GoodsItem(goods.getGoodsId(), goods.getSellerId(), goods.getTitle(), goods.getCategory(),
                goods.getPrice(), goods.getDescription(), goods.getImages(), goods.getStatus(),
                goods.getCreateTime());
    }
}
