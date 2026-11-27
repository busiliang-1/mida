package cn.iocoder.yudao.module.mate.service.im;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.mate.dal.dataobject.im.ImMessageDO;
import cn.iocoder.yudao.module.mate.dal.dataobject.im.ImSessionDO;
import cn.iocoder.yudao.module.mate.dal.dataobject.im.ImSessionMemberDO;
import cn.iocoder.yudao.module.mate.dal.dataobject.im.NotifyRecordDO;
import cn.iocoder.yudao.module.mate.dal.mysql.im.ImBlacklistMapper;
import cn.iocoder.yudao.module.mate.dal.mysql.im.ImMessageMapper;
import cn.iocoder.yudao.module.mate.dal.mysql.im.ImSessionMapper;
import cn.iocoder.yudao.module.mate.dal.mysql.im.ImSessionMemberMapper;
import cn.iocoder.yudao.module.mate.dal.mysql.im.NotifyRecordMapper;
import cn.iocoder.yudao.module.mate.service.risk.SensitiveWordService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * M3 消息核心约束单测：
 * 1) 同 clientMsgId 只落 1 行（含并发撞唯一键的路径）；
 * 2) 双向拉黑后发送被拦截；
 * 3) 撤回超过 2 分钟返回 2002；
 * 4) 卡片校验不通过时（伪造 bizNo）不落库。
 */
@ExtendWith(MockitoExtension.class)
class ImMessageServiceImplTest {

    private static final Long ME = 1L;
    private static final Long PEER = 2L;
    private static final Long SESSION_ID = 9001L;

    @Mock
    private ImSessionMapper sessionMapper;
    @Mock
    private ImMessageMapper messageMapper;
    @Mock
    private ImSessionMemberMapper memberMapper;
    @Mock
    private ImBlacklistMapper blacklistMapper;
    @Mock
    private NotifyRecordMapper notifyRecordMapper;
    @Mock
    private ImCardService cardService;
    @Mock
    private SensitiveWordService sensitiveWordService;
    @Mock
    private ImPushService pushService;
    @Mock
    private ImSessionService sessionService;

    @InjectMocks
    private ImMessageServiceImpl messageService;

    private static ImMessageService.SendCmd textCmd(String clientMsgId) {
        return new ImMessageService.SendCmd(SESSION_ID, 1, "你好", null, null, clientMsgId);
    }

    private void mockSingleSession() {
        when(sessionMapper.selectById(SESSION_ID)).thenReturn(ImSessionDO.builder()
                .sessionId(SESSION_ID).type(1).status(0).maxSequence(10L).memberCount(2).build());
        when(memberMapper.selectBySessionAndUser(SESSION_ID, ME)).thenReturn(ImSessionMemberDO.builder()
                .sessionId(SESSION_ID).userId(ME).lastReadSeq(0L).build());
        when(sessionService.listMemberIds(SESSION_ID)).thenReturn(List.of(ME, PEER));
        when(blacklistMapper.existsEitherDirection(ME, PEER)).thenReturn(false);
        lenient().when(sensitiveWordService.match(anyString()))
                .thenReturn(new SensitiveWordService.AuditResult(SensitiveWordService.AuditResult.PASS, List.of(), 1));
        lenient().when(notifyRecordMapper.insert(any(NotifyRecordDO.class))).thenReturn(1);
    }

    @Test
    void duplicateClientMsgIdReturnsFirstMessageWithoutInsert() {
        mockSingleSession();
        ImMessageDO existing = ImMessageDO.builder().messageId(100L).sessionId(SESSION_ID).seq(7L)
                .senderId(ME).contentType(1).content("你好").clientMsgId("c-1").status(1).build();
        when(messageMapper.selectByClientMsgId(SESSION_ID, "c-1")).thenReturn(existing);

        ImMessageService.SendResult result = messageService.send(ME, textCmd("c-1"));

        assertEquals(100L, result.messageId());
        assertEquals(7L, result.seq());
        verify(messageMapper, never()).insert(any(ImMessageDO.class));
    }

    @Test
    void concurrentDuplicateClientMsgIdCountsAsSuccess() {
        mockSingleSession();
        ImMessageDO existing = ImMessageDO.builder().messageId(101L).sessionId(SESSION_ID).seq(8L)
                .senderId(ME).contentType(1).content("你好").clientMsgId("c-2").status(1).build();
        when(messageMapper.selectByClientMsgId(SESSION_ID, "c-2")).thenReturn(null).thenReturn(existing);
        when(sessionMapper.incrementSequence(SESSION_ID)).thenReturn(1);
        when(sessionMapper.selectMaxSequence(SESSION_ID)).thenReturn(8L);
        when(messageMapper.insert(any(ImMessageDO.class))).thenThrow(new DuplicateKeyException("uk_session_client"));

        ImMessageService.SendResult result = messageService.send(ME, textCmd("c-2"));

        assertEquals(101L, result.messageId());
        // 插入只尝试一次，不重复落库，也不把唯一键冲突抛给前端
        verify(messageMapper, times(1)).insert(any(ImMessageDO.class));
        verify(notifyRecordMapper, never()).insert(any(NotifyRecordDO.class));
    }

    @Test
    void blockedUserCannotSend() {
        mockSingleSession();
        when(blacklistMapper.existsEitherDirection(ME, PEER)).thenReturn(true);

        ServiceException ex = assertThrows(ServiceException.class, () -> messageService.send(ME, textCmd("c-3")));

        assertEquals(1004, ex.getCode());
        verify(messageMapper, never()).insert(any(ImMessageDO.class));
    }

    @Test
    void sensitiveContentRejected() {
        mockSingleSession();
        when(sensitiveWordService.match(anyString()))
                .thenReturn(new SensitiveWordService.AuditResult(SensitiveWordService.AuditResult.HIT, List.of("刷单"), 1));

        ServiceException ex = assertThrows(ServiceException.class, () -> messageService.send(ME, textCmd("c-4")));

        assertEquals(4001, ex.getCode());
        verify(messageMapper, never()).insert(any(ImMessageDO.class));
    }

    @Test
    void forgedCardBizNoRejected() {
        mockSingleSession();
        org.mockito.Mockito.doThrow(new ServiceException(1004, "权限不足或资源归属不符"))
                .when(cardService).validateOrThrow(eq(1), eq("999"), eq(ME));
        ImMessageService.SendCmd cardCmd = new ImMessageService.SendCmd(
                SESSION_ID, 3, null, 1, "999", "c-5");

        ServiceException ex = assertThrows(ServiceException.class, () -> messageService.send(ME, cardCmd));

        assertEquals(1004, ex.getCode());
        verify(messageMapper, never()).insert(any(ImMessageDO.class));
    }

    @Test
    void recallAfterTwoMinutesRejected() {
        ImMessageDO message = ImMessageDO.builder().messageId(200L).sessionId(SESSION_ID).seq(3L)
                .senderId(ME).contentType(1).status(1).build();
        message.setCreateTime(LocalDateTime.now().minusMinutes(3));
        when(messageMapper.selectById(200L)).thenReturn(message);

        ServiceException ex = assertThrows(ServiceException.class, () -> messageService.recall(ME, 200L));

        assertEquals(2002, ex.getCode());
        verify(messageMapper, never()).updateById(any(ImMessageDO.class));
    }

    @Test
    void recallByOtherUserRejected() {
        ImMessageDO message = ImMessageDO.builder().messageId(201L).sessionId(SESSION_ID).seq(3L)
                .senderId(PEER).contentType(1).status(1).build();
        message.setCreateTime(LocalDateTime.now());
        when(messageMapper.selectById(201L)).thenReturn(message);

        ServiceException ex = assertThrows(ServiceException.class, () -> messageService.recall(ME, 201L));

        assertEquals(1004, ex.getCode());
    }

    @Test
    void recallWithinTwoMinutesSucceeds() {
        ImMessageDO message = ImMessageDO.builder().messageId(202L).sessionId(SESSION_ID).seq(4L)
                .senderId(ME).contentType(1).status(1).build();
        message.setCreateTime(LocalDateTime.now().minusSeconds(30));
        when(messageMapper.selectById(202L)).thenReturn(message);
        when(messageMapper.updateById(any(ImMessageDO.class))).thenReturn(1);
        when(sessionService.listMemberIds(SESSION_ID)).thenReturn(List.of(ME, PEER));

        assertEquals(true, messageService.recall(ME, 202L));
        verify(messageMapper, times(1)).updateById(any(ImMessageDO.class));
    }

    @Test
    void sequenceAssignedFromSessionMaxSequence() {
        mockSingleSession();
        when(messageMapper.selectByClientMsgId(SESSION_ID, "c-6")).thenReturn(null);
        when(sessionMapper.incrementSequence(SESSION_ID)).thenReturn(1);
        when(sessionMapper.selectMaxSequence(SESSION_ID)).thenReturn(11L);
        when(messageMapper.insert(any(ImMessageDO.class))).thenReturn(1);

        ImMessageService.SendResult result = messageService.send(ME, textCmd("c-6"));

        assertEquals(11L, result.seq());
        assertEquals(1, result.status()); // 已送达：落库成功才返回
        verify(sessionMapper, times(1)).incrementSequence(anyLong());
    }
}
