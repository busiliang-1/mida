package cn.iocoder.yudao.module.mate.enums.im;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;
import java.util.Objects;

/**
 * M3 消息状态。文档：0发送中1已送达2失败3撤回。
 * 只有 DELIVERED 状态、且发送时间在 2 分钟内、且是本人，才允许转 RECALLED。
 */
@Getter
@AllArgsConstructor
public enum ImMessageStatusEnum {

    SENDING(0, "发送中"),
    DELIVERED(1, "已送达"),
    FAILED(2, "失败"),
    RECALLED(3, "撤回");

    private final Integer status;
    private final String name;

    public static ImMessageStatusEnum of(Integer status) {
        return Arrays.stream(values()).filter(e -> Objects.equals(e.status, status)).findFirst().orElse(null);
    }
}
