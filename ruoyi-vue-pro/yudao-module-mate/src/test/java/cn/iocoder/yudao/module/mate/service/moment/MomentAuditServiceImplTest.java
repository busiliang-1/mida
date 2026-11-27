package cn.iocoder.yudao.module.mate.service.moment;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.mate.dal.dataobject.moment.MomentCommentDO;
import cn.iocoder.yudao.module.mate.dal.dataobject.moment.MomentPostDO;
import cn.iocoder.yudao.module.mate.dal.mysql.moment.AuditQueueRow;
import cn.iocoder.yudao.module.mate.dal.mysql.moment.MomentAuditMapper;
import cn.iocoder.yudao.module.mate.dal.mysql.moment.MomentCommentMapper;
import cn.iocoder.yudao.module.mate.dal.mysql.moment.MomentPostMapper;
import cn.iocoder.yudao.module.mate.enums.moment.AuditStatusEnum;
import cn.iocoder.yudao.module.mate.enums.moment.PublishStatusEnum;
import cn.iocoder.yudao.module.mate.framework.web.CursorPage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * M5 审核（#58）测试。
 *
 * 核心口径：
 * - 通过时 audit_status 与 publish_status 必须一起变（同一 UPDATE），否则会出现"审核通过但仍不可见"
 * - 处置用 CAS 带前驱状态，重复处置返回 false 而不是覆盖别人的结论
 * - 队列游标只用 biz_id：两张表共用雪花生成器，ID 全局单调，等价于时间序
 */
@ExtendWith(MockitoExtension.class)
class MomentAuditServiceImplTest {

    private static final Long POST_ID = 9001L;
    private static final Long COMMENT_ID = 9002L;

    @Mock
    private MomentAuditMapper momentAuditMapper;
    @Mock
    private MomentPostMapper momentPostMapper;
    @Mock
    private MomentCommentMapper momentCommentMapper;

    @InjectMocks
    private MomentAuditServiceImpl momentAuditService;

    @Test
    void passPostAlsoPublishesIt() {
        when(momentPostMapper.selectById(POST_ID)).thenReturn(post(AuditStatusEnum.PENDING.getStatus()));
        when(momentPostMapper.advanceAudit(POST_ID, 0, 1, PublishStatusEnum.PUBLISHED.getStatus())).thenReturn(1);

        assertTrue(momentAuditService.dispose(MomentAuditService.TYPE_POST, POST_ID, 1));

        // 一个 UPDATE 里同时置审核通过 + 已发布，避免中间态被读到
        verify(momentPostMapper).advanceAudit(POST_ID, 0, 1, PublishStatusEnum.PUBLISHED.getStatus());
    }

    @Test
    void rejectPostKeepsItUnpublished() {
        when(momentPostMapper.selectById(POST_ID)).thenReturn(post(AuditStatusEnum.PENDING.getStatus()));
        when(momentPostMapper.advanceAudit(POST_ID, 0, 2, PublishStatusEnum.SELF_ONLY.getStatus())).thenReturn(1);

        assertTrue(momentAuditService.dispose(MomentAuditService.TYPE_POST, POST_ID, 2));

        verify(momentPostMapper).advanceAudit(POST_ID, 0, 2, PublishStatusEnum.SELF_ONLY.getStatus());
    }

    @Test
    void manualQueueItemCanStillBePassed() {
        when(momentPostMapper.selectById(POST_ID)).thenReturn(post(AuditStatusEnum.MANUAL.getStatus()));
        when(momentPostMapper.advanceAudit(POST_ID, 3, 1, PublishStatusEnum.PUBLISHED.getStatus())).thenReturn(1);

        assertTrue(momentAuditService.dispose(MomentAuditService.TYPE_POST, POST_ID, 1));
    }

    @Test
    void repeatedDisposeDoesNotOverwrite() {
        when(momentPostMapper.selectById(POST_ID)).thenReturn(post(AuditStatusEnum.PASSED.getStatus()));

        assertFalse(momentAuditService.dispose(MomentAuditService.TYPE_POST, POST_ID, 2));

        // 已通过的不能再被改判为驳回
        verify(momentPostMapper, never()).advanceAudit(anyLong(), anyInt(), anyInt(), anyInt());
    }

    @Test
    void invalidActionRejected() {
        ServiceException ex = assertThrows(ServiceException.class,
                () -> momentAuditService.dispose(MomentAuditService.TYPE_POST, POST_ID, 9));
        assertEquals(1001, ex.getCode());
    }

    @Test
    void unknownBizTypeRejected() {
        ServiceException ex = assertThrows(ServiceException.class,
                () -> momentAuditService.dispose("demand", POST_ID, 1));
        assertEquals(1001, ex.getCode());
    }

    @Test
    void disposeCommentPassesThrough() {
        when(momentCommentMapper.selectById(COMMENT_ID)).thenReturn(MomentCommentDO.builder()
                .commentId(COMMENT_ID).postId(POST_ID).auditStatus(0).content("hi").build());
        when(momentCommentMapper.advanceAudit(COMMENT_ID, 0, 1)).thenReturn(1);

        assertTrue(momentAuditService.dispose(MomentAuditService.TYPE_COMMENT, COMMENT_ID, 1));
    }

    @Test
    void queueTrimsAndReturnsCursor() {
        List<AuditQueueRow> rows = new ArrayList<>();
        for (long i = 0; i < 3; i++) {
            rows.add(row(POST_ID - i));
        }
        when(momentAuditMapper.selectQueue(null, null, 3)).thenReturn(rows);

        CursorPage<MomentAuditService.AuditItem> page = momentAuditService.queue(null, null, 2);

        assertEquals(2, page.list().size());
        assertTrue(page.hasMore());
        assertEquals(String.valueOf(POST_ID - 1), page.nextCursor());
        assertEquals(MomentAuditService.TYPE_POST, page.list().get(0).bizType());
    }

    @Test
    void queueWithoutMoreHasNoCursor() {
        when(momentAuditMapper.selectQueue(0, null, 51)).thenReturn(List.of(row(POST_ID)));
        CursorPage<MomentAuditService.AuditItem> page = momentAuditService.queue(0, null, 50);
        assertFalse(page.hasMore());
        assertNull(page.nextCursor());
    }

    @ParameterizedTest(name = "audit {0} -> {1} allowed={2}")
    @CsvSource({
            "0, 1, true",
            "0, 2, true",
            "0, 3, true",
            "3, 1, true",
            "3, 2, true",
            "1, 2, false",
            "2, 1, false",
            "1, 1, false"
    })
    void auditTransitions(int from, int to, boolean allowed) {
        assertEquals(allowed, AuditStatusEnum.canTransition(from, to));
    }

    private static MomentPostDO post(Integer auditStatus) {
        return MomentPostDO.builder()
                .postId(POST_ID).userId(1L).content("内容")
                .auditStatus(auditStatus)
                .publishStatus(PublishStatusEnum.SELF_ONLY.getStatus())
                .build();
    }

    private static AuditQueueRow row(Long bizId) {
        AuditQueueRow row = new AuditQueueRow();
        row.setBizType(MomentAuditService.TYPE_POST);
        row.setBizId(bizId);
        row.setUserId(1L);
        row.setAuditStatus(0);
        return row;
    }
}
