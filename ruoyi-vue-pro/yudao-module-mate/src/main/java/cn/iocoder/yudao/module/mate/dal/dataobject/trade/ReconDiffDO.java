package cn.iocoder.yudao.module.mate.dal.dataobject.trade;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.math.BigDecimal;

/**
 * M4 对账差错 DO。每日对账差异生成工单，人工处置只改 status。
 */
@TableName("recon_diff")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReconDiffDO extends BaseDO {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String diffNo;
    private String bizNo;
    private BigDecimal localAmount;
    private BigDecimal channelAmount;
    /** 1金额2状态3单边 */
    private Integer type;
    /** 0待处理1已处理 */
    private Integer status;
}
