package cn.iocoder.yudao.module.mate.enums.moment;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;
import java.util.Objects;

/**
 * M5 可见范围。同校（1）需要 viewer 与作者 school_id 一致才能看，仅自己（2）只有作者本人能看。
 */
@Getter
@AllArgsConstructor
public enum VisibleScopeEnum {

    PUBLIC(0, "公开"),
    SAME_SCHOOL(1, "同校"),
    SELF(2, "仅自己");

    private final Integer status;
    private final String name;

    public static VisibleScopeEnum of(Integer status) {
        return Arrays.stream(values()).filter(e -> Objects.equals(e.status, status)).findFirst().orElse(null);
    }
}
