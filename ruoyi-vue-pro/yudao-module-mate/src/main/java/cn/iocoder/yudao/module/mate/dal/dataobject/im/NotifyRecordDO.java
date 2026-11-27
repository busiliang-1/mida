package cn.iocoder.yudao.module.mate.dal.dataobject.im;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.time.LocalDateTime;

/**
 * M3 推送记录 DO。一期只做在线直推 + 站内红点，离线补拉由 im_message 历史接口兜底。
 */
@TableName("notify_record")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotifyRecordDO extends BaseDO {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    /** 接收者 */
    private Long userId;
    /** 1即时2聚合3定时 */
    private Integer level;
    private String bizType;
    private String payload;
    private LocalDateTime sentAt;
    /** 0未读1已读 */
    private Integer readFlag;
}
