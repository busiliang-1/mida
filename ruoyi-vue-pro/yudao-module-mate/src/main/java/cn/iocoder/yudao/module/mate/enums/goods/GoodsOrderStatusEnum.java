package cn.iocoder.yudao.module.mate.enums.goods;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * M7 二手订单状态机：0待确认 → 1已完成 / 2已取消。
 * 用前驱白名单校验，避免把已取消订单再确认完成（2002）。
 */
@Getter
@AllArgsConstructor
public enum GoodsOrderStatusEnum {

    CREATED(0, "待确认"),
    COMPLETED(1, "已完成"),
    CANCELLED(2, "已取消");

    private static final Map<Integer, Set<Integer>> TRANSITIONS = Map.of(
            0, Set.of(1, 2),
            1, Set.of(),
            2, Set.of());

    private final Integer status;
    private final String name;

    public static GoodsOrderStatusEnum of(Integer status) {
        return Arrays.stream(values()).filter(e -> Objects.equals(e.status, status)).findFirst().orElse(null);
    }

    public static boolean canTransition(Integer from, Integer to) {
        return TRANSITIONS.getOrDefault(from, Set.of()).contains(to);
    }
}
