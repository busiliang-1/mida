package cn.iocoder.yudao.module.mate.enums.trade;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;
import java.util.Objects;

/**
 * M4 转账状态。文档：0待支付1待到账2已到账3已撤销4已冻结。
 * 资金动作只允许：WAITING→PENDING_SETTLE/SETTLED、PENDING_SETTLE→SETTLED/CANCELLED，其余跃迁一律 2002。
 */
@Getter
@AllArgsConstructor
public enum TransferStatusEnum {

    WAITING_PAY(0, "待支付"),
    PENDING_SETTLE(1, "待到账"),
    SETTLED(2, "已到账"),
    CANCELLED(3, "已撤销"),
    FROZEN(4, "已冻结");

    private final Integer status;
    private final String name;

    public static TransferStatusEnum of(Integer status) {
        return Arrays.stream(values()).filter(e -> Objects.equals(e.status, status)).findFirst().orElse(null);
    }
}
