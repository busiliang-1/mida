package cn.iocoder.yudao.module.mate.dal.mysql.moment;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 审核队列投影：动态与评论共用一条队列，用 biz_type 区分。
 * 之所以能只用一个游标：两张表的主键都来自同一个雪花生成器，ID 全局单调递增，
 * 因此 "ORDER BY biz_id DESC" 天然等于"按提交时间倒序"，不需要复合游标。
 */
@Data
@NoArgsConstructor
public class AuditQueueRow {

    /** post / comment */
    private String bizType;
    private Long bizId;
    private Long userId;
    private String content;
    private Integer auditStatus;
    private LocalDateTime createTime;
}
