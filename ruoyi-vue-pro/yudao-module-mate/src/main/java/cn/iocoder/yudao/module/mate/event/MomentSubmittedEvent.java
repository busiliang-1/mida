package cn.iocoder.yudao.module.mate.event;

import java.time.LocalDateTime;

/**
 * 内容提交事件（对应《接口文档》#49/#53 的 content.submitted 语义）。
 *
 * <p>动态与评论写入后立即发出，作为"审核流水线"的接入点：
 * 当前审核走的同步通道是 #58 审核队列（直接查表，状态即事实），
 * 事件本身留给后续接 MQ / 风控打分 / 异步审核时消费，不改变主链路事务边界。
 *
 * @param bizId      动态 ID 或评论 ID
 * @param bizType    post / comment
 * @param userId     提交者
 * @param content    正文
 * @param auditStatus 提交时的审核状态（通常 0 待审）
 * @param submittedAt 提交时间
 */
public record MomentSubmittedEvent(Long bizId, String bizType, Long userId, String content,
                                   Integer auditStatus, LocalDateTime submittedAt) {

    public static final String TYPE_POST = "post";
    public static final String TYPE_COMMENT = "comment";
}
