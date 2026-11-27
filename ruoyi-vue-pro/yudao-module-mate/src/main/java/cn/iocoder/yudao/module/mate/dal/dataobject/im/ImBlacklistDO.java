package cn.iocoder.yudao.module.mate.dal.dataobject.im;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

/**
 * M3 拉黑 DO。唯一键 uk_user_blocked 防重复；阻断时查双向，任一方拉黑即不可发。
 */
@TableName("im_blacklist")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImBlacklistDO extends BaseDO {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    /** 拉黑发起者 */
    private Long userId;
    /** 被拉黑者 */
    private Long blockedUserId;
}
