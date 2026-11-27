package cn.iocoder.yudao.module.mate.service.im;

import cn.iocoder.yudao.module.mate.framework.web.CursorPage;

import java.time.LocalDateTime;

/**
 * M3 消息 Service。
 * 硬约束：INSERT 成功才算发送成功；同会话同 clientMsgId 只落一行；seq 会话内单调递增。
 */
public interface ImMessageService {

    SendResult send(Long userId, SendCmd cmd);

    /** 历史消息：cursor 为空取最新一页，cursor=seq 则取更早的一页 */
    CursorPage<MessageItem> history(Long userId, Long sessionId, String cursor, int size);

    /** 撤回：≤2 分钟且仅本人；重复撤回幂等成功 */
    boolean recall(Long userId, Long messageId);

    record SendCmd(Long sessionId, Integer contentType, String content, Integer cardType,
                   String bizNo, String clientMsgId) {
    }

    record SendResult(Long messageId, Long seq, Integer status) {
    }

    record MessageItem(Long messageId, Long sessionId, Long seq, Long senderId, Integer contentType,
                       String content, Integer cardType, String bizNo, Integer status,
                       LocalDateTime createdTime) {
    }
}
