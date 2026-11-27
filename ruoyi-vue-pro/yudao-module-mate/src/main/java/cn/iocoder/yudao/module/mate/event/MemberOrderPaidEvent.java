package cn.iocoder.yudao.module.mate.event;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 会员/增值订单支付成功事件（契约 order.paid）。
 *
 * <p>产生方：M4 支付回调（权益与订单已在同一事务内生效，本事件只做"已发生"的通知）。
 * 消费方：M6 信用分/统计、后续可能的风控画像。
 *
 * <p>幂等口径：消费方按 orderNo 去重——同一订单重复回调时本事件不会被重复发布，
 * 但消费方仍应按 orderNo 幂等，避免重放。
 *
 * @param eventId  事件唯一标识，取 orderNo（一单一次），消费方也可用作幂等键
 * @param orderNo  会员/增值订单号
 * @param userId   下单用户
 * @param amount   实付金额
 * @param orderType 1=会员 2=增值服务
 * @param paidAt   支付完成时间
 */
public record MemberOrderPaidEvent(String eventId, String orderNo, Long userId,
                                   BigDecimal amount, Integer orderType, LocalDateTime paidAt) {
}
