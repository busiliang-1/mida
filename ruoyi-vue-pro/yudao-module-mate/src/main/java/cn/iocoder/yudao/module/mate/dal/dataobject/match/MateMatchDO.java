package cn.iocoder.yudao.module.mate.dal.dataobject.match;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.time.LocalDateTime;

@TableName("mate_match")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MateMatchDO extends BaseDO {

    @TableId(type = IdType.ASSIGN_ID)
    private Long matchId;
    private Long userLowId;
    private Long userHighId;
    /** 1滑动2成团3附近 */
    private Integer source;
    private Integer greetingUsed;
    private LocalDateTime cooldownUntil;
    private Long sessionId;
}
