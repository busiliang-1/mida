package cn.iocoder.yudao.module.mate.dal.dataobject.risk;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

@TableName("risk_event")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RiskEventDO extends BaseDO {

    @TableId(type = IdType.ASSIGN_ID)
    private Long eventId;
    private Long userId;
    private String eventType;
    private Integer ruleId;
    private Integer ruleVersion;
    private Integer score;
    /** 0放行1延迟2冻结3转人工 */
    private Integer advice;
}
