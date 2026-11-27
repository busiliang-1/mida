package cn.iocoder.yudao.module.mate.enums.moment;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * M5 审核状态机。审核仅允许从"待审/转人工"推进到"通过/驳回"，
 * 已通过或已驳回不能再改判——否则一条已发布的内容可以被悄悄下线，前端无感知。
 */
@Getter
@AllArgsConstructor
public enum AuditStatusEnum {

    PENDING(0, "待审"),
    PASSED(1, "通过"),
    REJECTED(2, "驳回"),
    MANUAL(3, "转人工");

    /** 前驱白名单：只能从 待审/转人工 出发 */
    private static final Map<Integer, Set<Integer>> TRANSITIONS = Map.of(
            0, Set.of(1, 2, 3),
            3, Set.of(1, 2),
            1, Set.of(),
            2, Set.of());

    private final Integer status;
    private final String name;

    public static AuditStatusEnum of(Integer status) {
        return Arrays.stream(values()).filter(e -> Objects.equals(e.status, status)).findFirst().orElse(null);
    }

    public static boolean canTransition(Integer from, Integer to) {
        return TRANSITIONS.getOrDefault(from, Set.of()).contains(to);
    }

    /** 是否还需要人工/自动审核 */
    public static boolean isAuditable(Integer status) {
        return Objects.equals(PENDING.status, status) || Objects.equals(MANUAL.status, status);
    }
}
