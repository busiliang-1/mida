package cn.iocoder.yudao.module.mate.framework.trade;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * 分摊金额拆分：人均保留两位小数，误差全部由最后一人补平，保证 Σamount == total。
 * 例：100 / 3 → [33.33, 33.33, 33.34]。
 */
public final class SplitAmountCalculator {

    private SplitAmountCalculator() {
    }

    public static List<BigDecimal> allocate(BigDecimal total, int count) {
        if (total == null || count <= 0) {
            throw new IllegalArgumentException("分摊总额与人数必须为正");
        }
        BigDecimal normalized = total.setScale(2, RoundingMode.HALF_UP);
        BigDecimal per = normalized.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP);
        List<BigDecimal> amounts = new ArrayList<>(count);
        BigDecimal accumulated = BigDecimal.ZERO;
        for (int i = 1; i < count; i++) {
            amounts.add(per);
            accumulated = accumulated.add(per);
        }
        amounts.add(normalized.subtract(accumulated));
        return amounts;
    }
}
