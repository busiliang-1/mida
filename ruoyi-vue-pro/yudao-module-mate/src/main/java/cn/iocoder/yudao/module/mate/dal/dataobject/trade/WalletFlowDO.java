package cn.iocoder.yudao.module.mate.dal.dataobject.trade;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.math.BigDecimal;

/**
 * M4 资金流水 DO。只追加不修改，balance_after 供对账复算。
 */
@TableName("wallet_flow")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WalletFlowDO extends BaseDO {

    @TableId(type = IdType.ASSIGN_ID)
    private Long flowId;
    private Long transferId;
    private Long userId;
    /** 1转出2转入 */
    private Integer direction;
    private BigDecimal amount;
    private BigDecimal balanceAfter;
    private String institutionSerial;
}
