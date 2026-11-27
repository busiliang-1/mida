package cn.iocoder.yudao.module.mate.service.moment;

import cn.iocoder.yudao.module.mate.framework.web.CursorPage;

import java.time.LocalDateTime;

/**
 * M5 内容审核 Service（#58 审核队列 + 处置）。
 *
 * <p>队列不建独立审核表：待审状态本来就落在 moment_post.audit_status / moment_comment.audit_status 上，
 * 再造一张队列表必然出现"队列说待审、业务表说通过"的双写不一致。队列直接用 UNION 查询派生。
 */
public interface MomentAuditService {

    String TYPE_POST = "post";
    String TYPE_COMMENT = "comment";

    record AuditItem(String bizType, Long bizId, Long userId, String content,
                     Integer auditStatus, LocalDateTime createTime) {
    }

    /** status 为空时返回"待审 + 转人工"（真正需要处置的内容） */
    CursorPage<AuditItem> queue(Integer status, String cursor, int size);

    /**
     * 处置：action=1 通过（动态同步置为已发布）/ 2 驳回。
     * 只允许处置待审或转人工的内容，重复处置返回 false 而不是报错。
     */
    boolean dispose(String bizType, Long bizId, Integer action);
}
