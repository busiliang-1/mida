package cn.iocoder.yudao.module.mate.dal.dataobject.moment;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

/**
 * M5 动态 DO。
 * 审核态（audit_status）与发布态（publish_status）双列并存，是"先审后发"的落库形态：
 * 新动态 audit_status=0 + publish_status=0（仅自己可见），审核通过才把 publish_status 置 1。
 * 信息流只查 publish_status=1，因此待审内容永远不会被别人看到，也不需要在 SQL 里再做审核判断。
 */
@TableName("moment_post")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MomentPostDO extends BaseDO {

    @TableId(type = IdType.ASSIGN_ID)
    private Long postId;
    private Long userId;
    private String content;
    private Integer topicId;
    /** 0待审1通过2驳回3转人工 */
    private Integer auditStatus;
    /** 0仅自己可见1已发布2已下架 */
    private Integer publishStatus;
    /** 0公开1同校2仅自己 */
    private Integer visibleScope;
}
