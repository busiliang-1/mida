package cn.iocoder.yudao.module.mate.dal.dataobject.im;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

/**
 * M3 会话 DO。
 * session_id / max_sequence 是并发热点：发消息时先对 max_sequence 做自增（行锁串行化），再插消息。
 */
@TableName("im_session")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImSessionDO extends BaseDO {

    @TableId(type = IdType.ASSIGN_ID)
    private Long sessionId;
    /** 1单聊2群聊 */
    private Integer type;
    /** 群聊关联需求单 */
    private Long demandId;
    /** 单聊关联匹配 */
    private Long matchId;
    private Integer memberCount;
    /** 当前最大序列号，会话内单调递增 */
    private Long maxSequence;
    /** 0正常1封禁2归档 */
    private Integer status;
}
