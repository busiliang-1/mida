package cn.iocoder.yudao.module.mate.dal.dataobject.risk;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.time.LocalDateTime;

@TableName("risk_blacklist")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RiskBlacklistDO extends BaseDO {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long userId;
    /** 1设备2IP3账号 */
    private Integer type;
    private String reason;
    private LocalDateTime expireAt;
}
