package cn.iocoder.yudao.module.mate.event;

import java.math.BigDecimal;

/**
 * 二手成交事件。仅用于信用分统计/推荐，不触发任何钱包动作（无资金托管）。
 */
public record GoodsSoldEvent(Long goodsId, Long orderId, Long sellerId, Long buyerId, BigDecimal amount) {
}
