package cn.iocoder.yudao.module.mate.dal.dataobject.moment;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

/**
 * M5 动态评论 DO。评论同样进审核：audit_status=0 时只在详情页对自己可见。
 */
@TableName("moment_comment")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MomentCommentDO extends BaseDO {

    @TableId(type = IdType.ASSIGN_ID)
    private Long commentId;
    private Long postId;
    private Long userId;
    /** 父评论，只做两级（父必须是顶层评论） */
    private Long parentId;
    private String content;
    /** 0待审1通过2驳回 */
    private Integer auditStatus;
}
