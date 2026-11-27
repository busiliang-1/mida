package cn.iocoder.yudao.module.mate.framework.trade;

import java.math.BigDecimal;

/**
 * M4 资金策略常量（一期硬编码，后续可迁 sys_config）。
 * 等级、限额、风控是三道独立闸门，顺序必须是 等级 → 限额 → 风控，否则 TC-06（L1+超限）会被限额码抢先。
 */
public final class TransferLimits {

    /** 单笔上限 */
    public static final BigDecimal SINGLE_LIMIT = new BigDecimal("2000.00");
    /** 当日累计上限（转出） */
    public static final BigDecimal DAILY_LIMIT = new BigDecimal("5000.00");
    /** 中风险/新关系的延迟到账时长 */
    public static final long DELAY_HOURS = 24;

    private TransferLimits() {
    }
}
