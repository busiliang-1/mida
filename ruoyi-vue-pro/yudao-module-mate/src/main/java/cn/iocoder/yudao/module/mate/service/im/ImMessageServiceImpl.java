package cn.iocoder.yudao.module.mate.service.im;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.module.mate.dal.dataobject.im.ImMessageDO;
import cn.iocoder.yudao.module.mate.dal.dataobject.im.ImSessionDO;
import cn.iocoder.yudao.module.mate.dal.dataobject.im.ImSessionMemberDO;
import cn.iocoder.yudao.module.mate.dal.dataobject.im.NotifyRecordDO;
import cn.iocoder.yudao.module.mate.dal.mysql.im.ImBlacklistMapper;
import cn.iocoder.yudao.module.mate.dal.mysql.im.ImMessageMapper;
import cn.iocoder.yudao.module.mate.dal.mysql.im.ImSessionMapper;
import cn.iocoder.yudao.module.mate.dal.mysql.im.ImSessionMemberMapper;
import cn.iocoder.yudao.module.mate.dal.mysql.im.NotifyRecordMapper;
import cn.iocoder.yudao.module.mate.enums.im.ImContentTypeEnum;
import cn.iocoder.yudao.module.mate.enums.im.ImMessageStatusEnum;
import cn.iocoder.yudao.module.mate.enums.im.ImSessionTypeEnum;
import cn.iocoder.yudao.module.mate.framework.web.CursorPage;
import cn.iocoder.yudao.module.mate.service.risk.SensitiveWordService;
import jakarta.annotation.Resource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.mate.enums.ErrorCodeConstants.CONTENT_AUDIT_HIT;
import static cn.iocoder.yudao.module.mate.enums.ErrorCodeConstants.PARAM_INVALID;
import static cn.iocoder.yudao.module.mate.enums.ErrorCodeConstants.RESOURCE_OWNER_MISMATCH;
import static cn.iocoder.yudao.module.mate.enums.ErrorCodeConstants.RESOURCE_STATUS_INVALID;

/**
 * M3 消息实现。并发与幂等设计：
 * 1) seq 由 im_session.max_sequence 在事务内自增（UPDATE 行锁串行化），uk_session_seq 兜底；
 * 2) clientMsgId 先查后插，插入撞 uk_session_client 时回查首次结果并当成功返回（不报错、不重复落库）；
 * 3) 发送前先查双向拉黑，再校验卡片归属，最后才落库，任一失败都不产生消息行。
 */
@Service
@Validated
public class ImMessageServiceImpl implements ImMessageService {

    private static final int MAX_PAGE_SIZE = 50;
    private static final int MAX_SEQ_ATTEMPTS = 3;
    private static final long RECALL_WINDOW_MINUTES = 2;
    private static final int CONTENT_MAX_LENGTH = 1000;
    private static final int CLIENT_MSG_ID_MAX_LENGTH = 64;

    @Resource
    private ImSessionMapper sessionMapper;
    @Resource
    private ImMessageMapper messageMapper;
    @Resource
    private ImSessionMemberMapper memberMapper;
    @Resource
    private ImBlacklistMapper blacklistMapper;
    @Resource
    private NotifyRecordMapper notifyRecordMapper;
    @Resource
    private ImCardService cardService;
    @Resource
    private SensitiveWordService sensitiveWordService;
    @Resource
    private ImPushService pushService;
    @Resource
    private ImSessionService sessionService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SendResult send(Long userId, SendCmd cmd) {
        validateCmd(cmd);
        ImSessionDO session = sessionMapper.selectById(cmd.sessionId());
        if (session == null) {
            throw exception(RESOURCE_OWNER_MISMATCH);
        }
        if (!Objects.equals(session.getStatus(), 0)) {
            throw exception(RESOURCE_STATUS_INVALID);
        }
        ImSessionMemberDO membership = memberMapper.selectBySessionAndUser(cmd.sessionId(), userId);
        if (membership == null) {
            throw exception(RESOURCE_OWNER_MISMATCH);
        }
        List<Long> memberIds = sessionService.listMemberIds(cmd.sessionId());
        assertNotBlocked(userId, session, memberIds);

        if (Objects.equals(cmd.contentType(), ImContentTypeEnum.TEXT.getType())) {
            assertContentPass(cmd.content());
        } else if (Objects.equals(cmd.contentType(), ImContentTypeEnum.CARD.getType())) {
            // 防伪：只认 cardType + bizNo，归属/状态由对应域校验器判定，非法直接 1004
            cardService.validateOrThrow(cmd.cardType(), cmd.bizNo(), userId);
        }

        // 幂等快路径：重试/重放直接返回首次消息
        ImMessageDO existing = messageMapper.selectByClientMsgId(cmd.sessionId(), cmd.clientMsgId());
        if (existing != null) {
            return toResult(existing);
        }

        InsertOutcome outcome = insertWithSequence(userId, cmd);
        // 只有真正新插入的消息才推送/记红点：并发重复请求命中唯一键时不能重复打扰对方
        if (outcome.created()) {
            afterSent(session, outcome.message(), memberIds);
        }
        return toResult(outcome.message());
    }

    @Override
    public CursorPage<MessageItem> history(Long userId, Long sessionId, String cursor, int size) {
        sessionService.assertMember(userId, sessionId);
        Long beforeSeq = parseSeq(cursor);
        int pageSize = size <= 0 ? 20 : Math.min(size, MAX_PAGE_SIZE);
        // 多取一条判断 hasMore，避免额外 count 查询
        List<ImMessageDO> rows = new ArrayList<>(
                messageMapper.selectHistory(sessionId, beforeSeq, pageSize + 1));
        boolean hasMore = rows.size() > pageSize;
        if (hasMore) {
            rows = new ArrayList<>(rows.subList(0, pageSize));
        }
        if (!rows.isEmpty()) {
            // 拉历史即视为已读到位，未读数随之归零
            sessionService.markRead(userId, sessionId, rows.get(0).getSeq());
        }
        String nextCursor = hasMore && !rows.isEmpty()
                ? String.valueOf(rows.get(rows.size() - 1).getSeq()) : null;
        List<MessageItem> items = rows.stream().map(this::toItem).toList();
        return new CursorPage<>(items, nextCursor, hasMore);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean recall(Long userId, Long messageId) {
        ImMessageDO message = messageMapper.selectById(messageId);
        if (message == null) {
            throw exception(RESOURCE_OWNER_MISMATCH);
        }
        if (!Objects.equals(message.getSenderId(), userId)) {
            throw exception(RESOURCE_OWNER_MISMATCH);
        }
        // 已撤回：幂等成功
        if (Objects.equals(message.getStatus(), ImMessageStatusEnum.RECALLED.getStatus())) {
            return true;
        }
        LocalDateTime deadline = message.getCreateTime().plusMinutes(RECALL_WINDOW_MINUTES);
        if (LocalDateTime.now().isAfter(deadline)) {
            throw exception(RESOURCE_STATUS_INVALID);
        }
        ImMessageDO update = new ImMessageDO();
        update.setMessageId(messageId);
        update.setStatus(ImMessageStatusEnum.RECALLED.getStatus());
        messageMapper.updateById(update);

        List<Long> memberIds = sessionService.listMemberIds(message.getSessionId());
        pushRecall(message, memberIds);
        return true;
    }

    // ========== 内部方法 ==========

    private void validateCmd(SendCmd cmd) {
        if (cmd == null || cmd.sessionId() == null || ImContentTypeEnum.of(cmd.contentType()) == null) {
            throw exception(PARAM_INVALID);
        }
        if (StrUtil.isBlank(cmd.clientMsgId()) || cmd.clientMsgId().length() > CLIENT_MSG_ID_MAX_LENGTH) {
            throw exception(PARAM_INVALID);
        }
        if (Objects.equals(cmd.contentType(), ImContentTypeEnum.TEXT.getType())
                && (StrUtil.isBlank(cmd.content()) || cmd.content().length() > CONTENT_MAX_LENGTH)) {
            throw exception(PARAM_INVALID);
        }
        if (Objects.equals(cmd.contentType(), ImContentTypeEnum.CARD.getType())
                && (cmd.cardType() == null || StrUtil.isBlank(cmd.bizNo()))) {
            throw exception(PARAM_INVALID);
        }
    }

    /** 双向拉黑：单聊查双方任一方向，群聊查是否被任一成员拉黑 */
    private void assertNotBlocked(Long userId, ImSessionDO session, List<Long> memberIds) {
        if (Objects.equals(session.getType(), ImSessionTypeEnum.SINGLE.getType())) {
            Long peerId = memberIds.stream().filter(id -> !Objects.equals(id, userId)).findFirst().orElse(null);
            if (peerId != null && blacklistMapper.existsEitherDirection(userId, peerId)) {
                throw exception(RESOURCE_OWNER_MISMATCH);
            }
            return;
        }
        for (Long memberId : memberIds) {
            if (!Objects.equals(memberId, userId) && blacklistMapper.existsBlock(memberId, userId)) {
                throw exception(RESOURCE_OWNER_MISMATCH);
            }
        }
    }

    private void assertContentPass(String content) {
        SensitiveWordService.AuditResult audit = sensitiveWordService.match(content);
        if (SensitiveWordService.AuditResult.HIT.equals(audit.verdict())) {
            throw exception(CONTENT_AUDIT_HIT);
        }
    }

    /**
     * 分配 seq 并落库。DuplicateKeyException 两种来源：
     * - uk_session_client：并发同 clientMsgId，回查首次结果直接返回（created=false，不再推送）；
     * - uk_session_seq：理论不会发生（会话行锁已串行化），重试即可。
     */
    private InsertOutcome insertWithSequence(Long userId, SendCmd cmd) {
        for (int attempt = 0; attempt < MAX_SEQ_ATTEMPTS; attempt++) {
            sessionMapper.incrementSequence(cmd.sessionId());
            Long seq = sessionMapper.selectMaxSequence(cmd.sessionId());
            if (seq == null) {
                throw exception(RESOURCE_OWNER_MISMATCH);
            }
            ImMessageDO message = ImMessageDO.builder()
                    .sessionId(cmd.sessionId())
                    .seq(seq)
                    .senderId(userId)
                    .contentType(cmd.contentType())
                    .content(Objects.equals(cmd.contentType(), ImContentTypeEnum.TEXT.getType())
                            ? cmd.content() : null)
                    .cardType(Objects.equals(cmd.contentType(), ImContentTypeEnum.CARD.getType())
                            ? cmd.cardType() : null)
                    .bizNo(Objects.equals(cmd.contentType(), ImContentTypeEnum.CARD.getType())
                            ? cmd.bizNo() : null)
                    .clientMsgId(cmd.clientMsgId())
                    .status(ImMessageStatusEnum.DELIVERED.getStatus())
                    .build();
            try {
                messageMapper.insert(message);
                return new InsertOutcome(message, true);
            } catch (DuplicateKeyException ex) {
                ImMessageDO duplicated = messageMapper.selectByClientMsgId(cmd.sessionId(), cmd.clientMsgId());
                if (duplicated != null) {
                    return new InsertOutcome(duplicated, false);
                }
            }
        }
        throw exception(RESOURCE_STATUS_INVALID);
    }

    /** 站内红点 + 在线直推：消息已落库后才执行，推送失败不影响发送结果 */
    private void afterSent(ImSessionDO session, ImMessageDO message, List<Long> memberIds) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "message");
        payload.put("sessionId", message.getSessionId());
        payload.put("messageId", message.getMessageId());
        payload.put("seq", message.getSeq());
        payload.put("senderId", message.getSenderId());
        payload.put("contentType", message.getContentType());
        payload.put("content", message.getContent());
        payload.put("cardType", message.getCardType());
        payload.put("bizNo", message.getBizNo());
        payload.put("status", message.getStatus());
        payload.put("createdTime", message.getCreateTime());
        for (Long memberId : memberIds) {
            if (Objects.equals(memberId, message.getSenderId())) {
                continue;
            }
            notifyRecordMapper.insert(NotifyRecordDO.builder()
                    .userId(memberId).level(1).bizType("im_message")
                    .payload("session=" + session.getSessionId() + ",seq=" + message.getSeq())
                    .sentAt(LocalDateTime.now()).readFlag(0).build());
            pushQuietly(memberId, payload);
        }
    }

    private void pushRecall(ImMessageDO message, List<Long> memberIds) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "recall");
        payload.put("sessionId", message.getSessionId());
        payload.put("messageId", message.getMessageId());
        payload.put("seq", message.getSeq());
        payload.put("status", ImMessageStatusEnum.RECALLED.getStatus());
        for (Long memberId : memberIds) {
            pushQuietly(memberId, payload);
        }
    }

    /** 推送是增强能力：离线/未连接不应让发送成功变成失败 */
    private void pushQuietly(Long userId, Object payload) {
        try {
            pushService.pushMessage(userId, payload);
        } catch (RuntimeException ignored) {
            // 在线推送失败由历史消息接口补齐，不影响落库结果
        }
    }

    private SendResult toResult(ImMessageDO message) {
        return new SendResult(message.getMessageId(), message.getSeq(), message.getStatus());
    }

    private MessageItem toItem(ImMessageDO message) {
        return new MessageItem(message.getMessageId(), message.getSessionId(), message.getSeq(),
                message.getSenderId(), message.getContentType(), message.getContent(),
                message.getCardType(), message.getBizNo(), message.getStatus(), message.getCreateTime());
    }

    /** 游标非法（非数字）按"从头"处理；seq 游标语义为"取更早的一页" */
    private Long parseSeq(String cursor) {
        if (StrUtil.isBlank(cursor)) {
            return null;
        }
        try {
            return Long.valueOf(cursor);
        } catch (NumberFormatException ex) {
            throw exception(PARAM_INVALID);
        }
    }

    /** created=false 表示命中了幂等键，消息不是本次新写入的 */
    private record InsertOutcome(ImMessageDO message, boolean created) {
    }
}
