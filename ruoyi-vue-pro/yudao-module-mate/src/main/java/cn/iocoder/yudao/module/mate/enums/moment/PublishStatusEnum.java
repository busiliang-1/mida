package cn.iocoder.yudao.module.mate.enums.moment;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;
import java.util.Objects;

/**
 * M5 发布状态。
 * 0仅自己可见（待审期间）→ 1已发布（审核通过）→ 2已下架（作者删除/违规下架）。
 */
@Getter
@AllArgsConstructor
public enum PublishStatusEnum {

    SELF_ONLY(0, "仅自己可见"),
    PUBLISHED(1, "已发布"),
    OFFLINE(2, "已下架");

    private final Integer status;
    private final String name;

    public static PublishStatusEnum of(Integer status) {
        return Arrays.stream(values()).filter(e -> Objects.equals(e.status, status)).findFirst().orElse(null);
    }
}
