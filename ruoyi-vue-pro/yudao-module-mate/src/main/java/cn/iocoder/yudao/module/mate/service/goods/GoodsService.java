package cn.iocoder.yudao.module.mate.service.goods;

import cn.iocoder.yudao.module.mate.framework.web.CursorPage;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * M7 二手 Service。无资金托管：下单只锁货，成交靠线下确认。
 */
public interface GoodsService {

    Long publish(Long sellerId, PublishCmd cmd);

    CursorPage<GoodsItem> list(String category, String cursor, int size);

    GoodsItem get(Long goodsId);

    Integer offShelf(Long sellerId, Long goodsId);

    OrderResult createOrder(Long buyerId, Long goodsId, String idempotencyKey);

    Integer confirm(Long userId, Long orderId);

    CursorPage<OrderItem> myOrders(Long userId, Integer role, String cursor, int size);

    record PublishCmd(String title, String category, BigDecimal price, String description, List<String> images) {
    }

    record GoodsItem(Long goodsId, Long sellerId, String title, String category, BigDecimal price,
                     String description, List<String> images, Integer status, LocalDateTime createTime) {
    }

    record OrderResult(String orderNo, Integer status) {
    }

    record OrderItem(Long orderId, String orderNo, Long goodsId, Long buyerId, Long sellerId,
                     BigDecimal amount, Integer status, LocalDateTime createTime) {
    }
}
