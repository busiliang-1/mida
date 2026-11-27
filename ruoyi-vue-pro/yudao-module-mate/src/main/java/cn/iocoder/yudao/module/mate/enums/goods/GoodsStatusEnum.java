package cn.iocoder.yudao.module.mate.enums.goods;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * M7 商品状态机。合法前驱白名单 + 非法跃迁抛 2002。
 * 0在售 → 1已锁定（下单）/ 3已下架（卖家下架）
 * 1已锁定 → 2已完成（线下确认）
 */
@Getter
@AllArgsConstructor
public enum GoodsStatusEnum {

    ON_SALE(0, "在售"),
    LOCKED(1, "已锁定"),
    SOLD(2, "已完成"),
    OFF(3, "已下架");

    private static final Map<Integer, Set<Integer>> TRANSITIONS = Map.of(
            0, Set.of(1, 3),
            1, Set.of(2),
            2, Set.of(),
            3, Set.of());

    private final Integer status;
    private final String name;

    public static GoodsStatusEnum of(Integer status) {
        return Arrays.stream(values()).filter(e -> Objects.equals(e.status, status)).findFirst().orElse(null);
    }

    public static boolean canTransition(Integer from, Integer to) {
        return TRANSITIONS.getOrDefault(from, Set.of()).contains(to);
    }
}
