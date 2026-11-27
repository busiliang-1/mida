package cn.iocoder.yudao.module.mate.dal.dataobject.trade;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.math.BigDecimal;

/**
 * M4 分摊单 DO。成团即建草稿，活动结束后填总额，全员确认后拆 N 笔 scene=2 转账。
 */
@TableName("trade_split_order")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TradeSplitOrderDO extends BaseDO {

    @TableId(type = IdType.ASSIGN_ID)
    private Long splitId;
    private String splitNo;
    private Long demandId;
    private Long initiatorId;
    private BigDecimal totalAmount;
    private Integer memberCount;
    private BigDecimal perAmount;
    /** 0草稿1待确认2收集中3已完成4已取消 */
    private Integer status;
}
