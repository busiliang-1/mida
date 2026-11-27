package cn.iocoder.yudao.module.mate.dal.dataobject.moment;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

/**
 * M5 动态附件 DO（只追加，不更新）。
 * 只存 object_key，不存完整 URL：域名/签名随 CDN 策略变化，落库会让历史图片全部失效。
 */
@TableName("moment_attachment")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MomentAttachmentDO extends BaseDO {

    @TableId(type = IdType.ASSIGN_ID)
    private Long attachmentId;
    private Long postId;
    /** 对象存储键，不存完整 URL */
    private String objectKey;
    /** 1图片2视频封面 */
    private Integer mediaType;
    private Integer width;
    private Integer height;
    /** 展示顺序，(post_id, sort_no) 唯一 */
    private Integer sortNo;
}
