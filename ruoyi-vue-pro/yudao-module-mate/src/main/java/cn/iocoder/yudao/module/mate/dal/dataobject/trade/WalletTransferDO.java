package cn.iocoder.yudao.module.mate.dal.dataobject.trade;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * M4 转账单 DO。transfer_no 唯一，既是业务单号也是幂等键。
 */
@TableName("wallet_transfer")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WalletTransferDO extends BaseDO {

    @TableId(type = IdType.ASSIGN_ID)
    private Long transferId;
    private String transferNo;
    private Long payerId;
    private Long payeeId;
    private Long sessionId;
    private Long splitId;
    private BigDecimal amount;
    /** 1直转2分摊3二手 */
    private Integer scene;
    /** 0待支付1待到账2已到账3已撤销4已冻结 */
    private Integer status;
    /** 0低1中2高 */
    private Integer riskLevel;
    private LocalDateTime delayUntil;
    private String remark;
    private LocalDateTime finishedAt;
}
