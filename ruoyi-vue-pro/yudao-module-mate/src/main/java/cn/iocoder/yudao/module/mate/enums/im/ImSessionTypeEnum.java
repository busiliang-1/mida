package cn.iocoder.yudao.module.mate.enums.im;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;
import java.util.Objects;

/**
 * M3 会话类型。文档：1单聊2群聊。
 */
@Getter
@AllArgsConstructor
public enum ImSessionTypeEnum {

    SINGLE(1, "单聊"),
    GROUP(2, "群聊");

    private final Integer type;
    private final String name;

    public static ImSessionTypeEnum of(Integer type) {
        return Arrays.stream(values()).filter(e -> Objects.equals(e.type, type)).findFirst().orElse(null);
    }
}
