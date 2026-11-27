package cn.iocoder.yudao.module.mate.enums.trade;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * M4 会员套餐目录（一期常量，后续可迁表/配置中心）。
 * 权益用 key-value 表达，发放时写 member_benefit，业务侧按 key 读取，新增权益不改代码。
 */
@Getter
@AllArgsConstructor
public enum MemberPackageEnum {

    BASIC(1, "基础会员", 1, new BigDecimal("9.90"),
            benefits(Map.of("publish_daily_limit", "5", "feed_weight", "1"))),
    PRO(2, "进阶会员", 3, new BigDecimal("25.90"),
            benefits(Map.of("publish_daily_limit", "20", "feed_weight", "2", "split_fee_free", "1"))),
    YEAR(3, "年度会员", 12, new BigDecimal("88.00"),
            benefits(Map.of("publish_daily_limit", "50", "feed_weight", "3", "split_fee_free", "1",
                    "goods_top", "1")));

    private final Integer packageId;
    private final String name;
    private final Integer months;
    private final BigDecimal price;
    /** 顺序稳定的权益表 */
    private final Map<String, String> benefits;

    public static MemberPackageEnum of(Integer packageId) {
        return Arrays.stream(values()).filter(e -> Objects.equals(e.packageId, packageId)).findFirst().orElse(null);
    }

    /** Map.of 顺序不稳定，统一转 LinkedHashMap 并按 key 排序，保证回调发放顺序可预期 */
    private static Map<String, String> benefits(Map<String, String> source) {
        Map<String, String> ordered = new LinkedHashMap<>();
        source.keySet().stream().sorted().forEach(key -> ordered.put(key, source.get(key)));
        return ordered;
    }
}
