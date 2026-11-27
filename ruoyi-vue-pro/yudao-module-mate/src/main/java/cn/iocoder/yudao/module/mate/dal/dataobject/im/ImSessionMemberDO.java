package cn.iocoder.yudao.module.mate.dal.dataobject.im;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

/**
 * M3 会话成员 DO。last_read_seq 用于未读数 = 会话 max_sequence - last_read_seq 的近似统计，
 * 精确统计走 im_message 的 seq > last_read_seq 且 sender_id != 当前用户。
 */
@TableName("im_session_member")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImSessionMemberDO extends BaseDO {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long sessionId;
    private Long userId;
    /** 0成员1群主 */
    private Integer role;
    private Long lastReadSeq;
}
