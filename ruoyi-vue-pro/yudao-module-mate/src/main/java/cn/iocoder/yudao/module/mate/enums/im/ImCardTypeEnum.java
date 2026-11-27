package cn.iocoder.yudao.module.mate.enums.im;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;
import java.util.Objects;

/**
 * M3 卡片类型。发卡片只传 cardType + bizNo，内容由服务端按单据实时渲染（防伪造快照）。
 */
@Getter
@AllArgsConstructor
public enum ImCardTypeEnum {

    DEMAND(1, "需求"),
    TRANSFER(2, "转账"),
    GOODS(3, "二手");

    private final Integer type;
    private final String name;

    public static ImCardTypeEnum of(Integer type) {
        return Arrays.stream(values()).filter(e -> Objects.equals(e.type, type)).findFirst().orElse(null);
    }
}
