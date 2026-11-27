package cn.iocoder.yudao.module.mate.dal.dataobject.moment;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

/**
 * M5 关注关系 DO，关注流（feed type=follow）的依据。
 * (follower_id, followee_id) 唯一，重复关注靠唯一键兜底而不是"先查后插"。
 */
@TableName("user_follow")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserFollowDO extends BaseDO {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    /** 关注者 */
    private Long followerId;
    /** 被关注者 */
    private Long followeeId;
}
