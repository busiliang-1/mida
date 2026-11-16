package cn.iocoder.yudao.module.mate.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Map;
import java.util.Set;

/**
 * 搭子需求单 7 态状态机 + 合法跃迁白名单。
 * 非法跃迁抛 2002。
 */
@Getter
@AllArgsConstructor
public enum DemandStatusEnum {

    DRAFT(0, "草稿"),
    AUDITING(1, "审核中"),
    RECRUITING(2, "招募中"),
    FORMED(3, "已成团"),
    ONGOING(4, "进行中"),
    FINISHED(5, "已结束"),
    CLOSED(6, "已关闭");

    private final int status;
    private final String name;

    /** 合法跃迁白名单：from -> 允许的 to 集合 */
    private static final Map<Integer, Set<Integer>> TRANSITIONS = Map.of(
            0, Set.of(1, 6),          // 草稿 -> 审核中 / 关闭
            1, Set.of(2, 6),          // 审核中 -> 招募中(通过) / 关闭(驳回)
            2, Set.of(3, 6),          // 招募中 -> 已成团 / 关闭(到期)
            3, Set.of(4, 2),          // 已成团 -> 进行中 / 回招募中(有人退出补员)
            4, Set.of(5),             // 进行中 -> 已结束
            5, Set.of(6)              // 已结束 -> 关闭
    );

    public static boolean canTransition(int from, int to) {
        return TRANSITIONS.getOrDefault(from, Set.of()).contains(to);
    }

    public static DemandStatusEnum of(int status) {
        for (DemandStatusEnum e : values()) {
            if (e.status == status) {
                return e;
            }
        }
        throw new IllegalArgumentException("未知需求单状态: " + status);
    }
}
