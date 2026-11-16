package cn.iocoder.yudao.module.mate.dal.dataobject.match;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@TableName("mate_demand")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MateDemandDO extends BaseDO {

    @TableId(type = IdType.ASSIGN_ID)
    private Long demandId;
    private Long publisherId;
    private String activityType;
    private String title;
    private String description;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String locationName;
    private BigDecimal longitude;
    private BigDecimal latitude;
    private String cityCode;
    private Integer needCount;
    private Integer joinedCount;
    private Integer genderLimit;
    private Integer authLimit;
    private Integer feeMode;
    /** 状态见 DemandStatusEnum */
    private Integer status;
}
