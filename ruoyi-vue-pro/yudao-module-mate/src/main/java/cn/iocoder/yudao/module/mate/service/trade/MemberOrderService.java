package cn.iocoder.yudao.module.mate.service.trade;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * M4 会员/增值订单 Service。
 * 支付回调必须与权益发放在同一事务：订单状态更新成功但权益没发，等价于用户付钱没拿到东西。
 */
public interface MemberOrderService {

    List<PackageItem> listPackages();

    CreateOrderResult createMemberOrder(Long userId, Integer packageId, Integer durationMonth, String idempotencyKey);

    CreateOrderResult createValueAddedOrder(Long userId, Integer type, String targetId, String idempotencyKey);

    /** 回调：验签 → event_id 去重 → 改单 + 发权益（同事务） */
    void handleCallback(String signature, CallbackCmd cmd, String rawBody);

    Integer getOrderStatus(Long userId, String orderNo);

    record PackageItem(Integer packageId, String name, Integer months, BigDecimal price,
                       Map<String, String> benefits) {
    }

    record CreateOrderResult(String orderNo, BigDecimal amount, Integer status, String payUrl) {
    }

    record CallbackCmd(String eventId, String orderNo, String tradeNo, BigDecimal amount,
                       String status, LocalDateTime occurredAt) {
    }
}
