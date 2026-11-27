package cn.iocoder.yudao.module.mate.service.trade;

import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.module.mate.dal.dataobject.trade.MemberOrderDO;
import cn.iocoder.yudao.module.mate.dal.dataobject.trade.TradePayNotifyDO;
import cn.iocoder.yudao.module.mate.dal.mysql.trade.MemberOrderMapper;
import cn.iocoder.yudao.module.mate.dal.mysql.trade.TradePayNotifyMapper;
import cn.iocoder.yudao.module.mate.enums.trade.MemberPackageEnum;
import cn.iocoder.yudao.module.mate.framework.trade.MockPayChannel;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.mate.enums.ErrorCodeConstants.PARAM_INVALID;
import static cn.iocoder.yudao.module.mate.enums.ErrorCodeConstants.PAY_SIGN_INVALID;
import static cn.iocoder.yudao.module.mate.enums.ErrorCodeConstants.RESOURCE_OWNER_MISMATCH;

/**
 * M4 会员订单实现。
 * 幂等两层：
 * 1) order_no = 前缀 + sha256(userId:Idempotency-Key)，同键同号，靠 uk_order_no 去重；
 * 2) 回调 event_id 唯一 + 订单已是"已生效"时直接返回，重复回调 10 次也只发一次权益。
 */
@Slf4j
@Service
@Validated
public class MemberOrderServiceImpl implements MemberOrderService {

    private static final int ORDER_NO_KEY_LENGTH = 24;
    private static final BigDecimal VALUE_ADDED_TOP_PRICE = new BigDecimal("6.00");
    private static final BigDecimal VALUE_ADDED_EXPOSE_PRICE = new BigDecimal("12.00");

    @Resource
    private MemberOrderMapper memberOrderMapper;
    @Resource
    private TradePayNotifyMapper payNotifyMapper;
    @Resource
    private MemberBenefitService memberBenefitService;
    @Resource
    private MockPayChannel payChannel;

    @Override
    public List<PackageItem> listPackages() {
        return Arrays.stream(MemberPackageEnum.values())
                .map(p -> new PackageItem(p.getPackageId(), p.getName(), p.getMonths(), p.getPrice(), p.getBenefits()))
                .toList();
    }

    @Override
    public CreateOrderResult createMemberOrder(Long userId, Integer packageId, Integer durationMonth,
                                              String idempotencyKey) {
        MemberPackageEnum pkg = MemberPackageEnum.of(packageId);
        if (pkg == null) {
            throw exception(PARAM_INVALID);
        }
        if (StrUtil.isBlank(idempotencyKey)) {
            throw exception(PARAM_INVALID);
        }
        int months = durationMonth == null ? pkg.getMonths() : durationMonth;
        if (months <= 0 || months > 36) {
            throw exception(PARAM_INVALID);
        }
        // 按时长比例计价，保留两位小数
        BigDecimal amount = pkg.getPrice().multiply(BigDecimal.valueOf(months))
                .divide(BigDecimal.valueOf(pkg.getMonths()), 2, RoundingMode.HALF_UP);
        String orderNo = "M" + digest(userId, idempotencyKey);
        MemberOrderDO existing = memberOrderMapper.selectByNo(orderNo);
        if (existing != null) {
            return toResult(existing);
        }
        MemberOrderDO order = MemberOrderDO.builder()
                .orderNo(orderNo).userId(userId).orderType(1).packageId(packageId)
                .durationMonth(months).amount(amount).payChannel(MockPayChannel.CHANNEL).status(0).build();
        try {
            memberOrderMapper.insert(order);
        } catch (DuplicateKeyException ex) {
            return toResult(memberOrderMapper.selectByNo(orderNo));
        }
        return toResult(order);
    }

    @Override
    public CreateOrderResult createValueAddedOrder(Long userId, Integer type, String targetId,
                                                   String idempotencyKey) {
        if (type == null || type < 1 || type > 2 || StrUtil.isBlank(idempotencyKey)) {
            throw exception(PARAM_INVALID);
        }
        BigDecimal amount = Objects.equals(type, 1) ? VALUE_ADDED_TOP_PRICE : VALUE_ADDED_EXPOSE_PRICE;
        String orderNo = "V" + digest(userId, idempotencyKey + ":" + type + ":" + targetId);
        MemberOrderDO existing = memberOrderMapper.selectByNo(orderNo);
        if (existing != null) {
            return toResult(existing);
        }
        MemberOrderDO order = MemberOrderDO.builder()
                .orderNo(orderNo).userId(userId).orderType(2).packageId(0).durationMonth(0)
                .amount(amount).payChannel(MockPayChannel.CHANNEL).status(0).build();
        try {
            memberOrderMapper.insert(order);
        } catch (DuplicateKeyException ex) {
            return toResult(memberOrderMapper.selectByNo(orderNo));
        }
        return toResult(order);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void handleCallback(String signature, CallbackCmd cmd, String rawBody) {
        if (cmd == null || StrUtil.isBlank(cmd.eventId()) || StrUtil.isBlank(cmd.orderNo())) {
            throw exception(PARAM_INVALID);
        }
        String canonical = MockPayChannel.canonical(cmd.eventId(), cmd.orderNo(), cmd.tradeNo(),
                cmd.amount(), cmd.status());
        payChannel.verify(canonical, signature);

        // 去重：event_id 唯一，重复回调直接成功返回
        if (payNotifyMapper.selectByEventId(cmd.eventId()) != null) {
            return;
        }
        try {
            payNotifyMapper.insert(TradePayNotifyDO.builder()
                    .eventId(cmd.eventId()).orderNo(cmd.orderNo()).tradeNo(cmd.tradeNo())
                    .amount(cmd.amount()).status(cmd.status()).rawBody(StrUtil.maxLength(rawBody, 990))
                    .handledAt(LocalDateTime.now()).build());
        } catch (DuplicateKeyException ex) {
            return; // 并发重复回调
        }
        if (!"SUCCESS".equalsIgnoreCase(cmd.status())) {
            // 失败/关闭：只记录，不改订单状态（前端可重新发起支付）
            return;
        }
        MemberOrderDO order = memberOrderMapper.selectByNo(cmd.orderNo());
        if (order == null) {
            throw exception(RESOURCE_OWNER_MISMATCH);
        }
        if (cmd.amount() == null || cmd.amount().compareTo(order.getAmount()) != 0) {
            // 金额不符：视为伪造/异常回调
            throw exception(PAY_SIGN_INVALID);
        }
        if (Objects.equals(order.getStatus(), 2)) {
            return; // 已生效：幂等，不重复发权益
        }

        LocalDateTime expireAt = order.getOrderType() != null && order.getOrderType() == 1
                ? memberBenefitService.nextExpireAt(order.getUserId(), order.getDurationMonth()) : null;
        MemberOrderDO update = new MemberOrderDO();
        update.setOrderId(order.getOrderId());
        update.setStatus(2);
        update.setPaidAmount(cmd.amount());
        update.setPayNo(cmd.tradeNo());
        update.setPaidAt(LocalDateTime.now());
        update.setExpireAt(expireAt);
        memberOrderMapper.updateById(update);

        // 同事务发权益：订单生效与权益记录一起成功/一起失败
        MemberPackageEnum pkg = MemberPackageEnum.of(order.getPackageId());
        if (order.getOrderType() != null && order.getOrderType() == 1 && pkg != null) {
            memberBenefitService.issue(order.getUserId(), order.getOrderNo(), pkg.getBenefits(), expireAt);
        }
    }

    @Override
    public Integer getOrderStatus(Long userId, String orderNo) {
        MemberOrderDO order = memberOrderMapper.selectByNo(orderNo);
        if (order == null || !Objects.equals(order.getUserId(), userId)) {
            throw exception(RESOURCE_OWNER_MISMATCH);
        }
        return order.getStatus();
    }

    private String digest(Long userId, String key) {
        return DigestUtil.sha256Hex(userId + ":" + key).substring(0, ORDER_NO_KEY_LENGTH).toUpperCase();
    }

    private CreateOrderResult toResult(MemberOrderDO order) {
        return new CreateOrderResult(order.getOrderNo(), order.getAmount(), order.getStatus(),
                payChannel.checkoutUrl(order.getOrderNo(), order.getAmount()));
    }
}
