package cn.iocoder.yudao.module.mate.enums.im;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;
import java.util.Objects;

/**
 * M3 消息内容类型。文档：1文本2图片3卡片。
 */
@Getter
@AllArgsConstructor
public enum ImContentTypeEnum {

    TEXT(1, "文本"),
    IMAGE(2, "图片"),
    CARD(3, "卡片");

    private final Integer type;
    private final String name;

    public static ImContentTypeEnum of(Integer type) {
        return Arrays.stream(values()).filter(e -> Objects.equals(e.type, type)).findFirst().orElse(null);
    }
}
