package cn.iocoder.yudao.module.mate.dal.dataobject.risk;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.time.LocalDateTime;

@TableName("risk_report")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RiskReportDO extends BaseDO {

    @TableId(type = IdType.ASSIGN_ID)
    private Long reportId;
    private Long reporterId;
    /** 1用户2动态3会话4转账5二手 */
    private Integer targetType;
    private Long targetId;
    private Integer reason;
    private String evidenceRef;
    /** 0待受理1处理中2已处置3已驳回 */
    private Integer status;
    private LocalDateTime deadlineAt;
}
