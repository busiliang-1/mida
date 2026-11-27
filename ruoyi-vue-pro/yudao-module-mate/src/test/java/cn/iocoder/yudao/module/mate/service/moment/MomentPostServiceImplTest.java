package cn.iocoder.yudao.module.mate.service.moment;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.mate.dal.dataobject.auth.SysUserDO;
import cn.iocoder.yudao.module.mate.dal.dataobject.moment.MomentAttachmentDO;
import cn.iocoder.yudao.module.mate.dal.dataobject.moment.MomentCommentDO;
import cn.iocoder.yudao.module.mate.dal.dataobject.moment.MomentPostDO;
import cn.iocoder.yudao.module.mate.dal.mysql.moment.MomentAttachmentMapper;
import cn.iocoder.yudao.module.mate.dal.mysql.moment.MomentCollectMapper;
import cn.iocoder.yudao.module.mate.dal.mysql.moment.MomentCommentMapper;
import cn.iocoder.yudao.module.mate.dal.mysql.moment.MomentLikeMapper;
import cn.iocoder.yudao.module.mate.dal.mysql.moment.MomentPostMapper;
import cn.iocoder.yudao.module.mate.enums.moment.AuditStatusEnum;
import cn.iocoder.yudao.module.mate.enums.moment.PublishStatusEnum;
import cn.iocoder.yudao.module.mate.event.MomentSubmittedEvent;
import cn.iocoder.yudao.module.mate.framework.web.CursorPage;
import cn.iocoder.yudao.module.mate.service.auth.AuthService;
import cn.iocoder.yudao.module.mate.service.risk.SensitiveWordService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * M5 动态主链路测试。
 *
 * 覆盖的关键口径：
 * - 先审后发：新动态必须是 audit_status=0 + publish_status=0（双状态同时落库），否则待审内容会直接上流
 * - 待审内容对他人 1004，对作者可见（作者要能看到"审核中"）
 * - 幂等：同 Idempotency-Key 重放只插入一次，返回首次 postId
 * - 图片只收 object_key，传完整 URL 直接 1001
 */
@ExtendWith(MockitoExtension.class)
class MomentPostServiceImplTest {

    private static final Long AUTHOR = 1L;
    private static final Long OTHER = 2L;
    private static final Long POST_ID = 9001L;

    @Mock
    private MomentPostMapper momentPostMapper;
    @Mock
    private MomentAttachmentMapper momentAttachmentMapper;
    @Mock
    private MomentCommentMapper momentCommentMapper;
    @Mock
    private MomentLikeMapper momentLikeMapper;
    @Mock
    private MomentCollectMapper momentCollectMapper;
    @Mock
    private MomentCounter momentCounter;
    @Mock
    private AuthService authService;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private StringRedisTemplate stringRedisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;

    /** 用真实词库测 4001，不 mock 掉敏感词判定本身 */
    @Spy
    private SensitiveWordService sensitiveWordService = new SensitiveWordService();

    @InjectMocks
    private MomentPostServiceImpl momentPostService;

    @BeforeEach
    void setUp() {
        // 部分用例在触发 Redis 之前就抛错，这里统一 lenient 避免 UnnecessaryStubbing
        lenient().when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().when(momentAttachmentMapper.selectByPostIds(anyCollection())).thenReturn(List.of());
        lenient().when(momentLikeMapper.selectLikedPostIds(anyLong(), anyCollection())).thenReturn(List.of());
        lenient().when(momentCollectMapper.selectCollectedPostIds(anyLong(), anyCollection())).thenReturn(List.of());
        lenient().when(momentCounter.likeCounts(anyCollection())).thenReturn(Map.of());
        lenient().when(momentCounter.commentCounts(anyCollection())).thenReturn(Map.of());
        lenient().when(momentCounter.collectCounts(anyCollection())).thenReturn(Map.of());
        lenient().when(authService.getAuthLevel(anyLong())).thenReturn(2);
    }

    // ==================== #49 发动态 ====================

    @Test
    void publishBelowLevel2Rejected() {
        when(authService.getAuthLevel(AUTHOR)).thenReturn(1);
        ServiceException ex = assertThrows(ServiceException.class, () -> momentPostService.publish(AUTHOR,
                new MomentPostService.PublishCmd("今天天气不错", List.of(), 1), "key-1"));
        assertEquals(1003, ex.getCode());
        verify(momentPostMapper, never()).insert(any(MomentPostDO.class));
    }

    @Test
    void publishSensitiveContentRejected() {
        ServiceException ex = assertThrows(ServiceException.class, () -> momentPostService.publish(AUTHOR,
                new MomentPostService.PublishCmd("想刷单的加微信", List.of(), 1), "key-2"));
        assertEquals(4001, ex.getCode());
        verify(momentPostMapper, never()).insert(any(MomentPostDO.class));
    }

    @Test
    void publishLandsBothAuditAndPublishStatus() {
        insertAssignsId(POST_ID);
        when(valueOperations.get(anyString())).thenReturn(null);
        when(valueOperations.setIfAbsent(anyString(), anyString(), any())).thenReturn(true);

        MomentPostService.PublishResult result = momentPostService.publish(AUTHOR,
                new MomentPostService.PublishCmd("求搭子一起自习", List.of("moment/a.jpg", "moment/b.jpg"), 7),
                "key-3");

        // 待审阶段必须 pending + self_only：任何一个漏掉都会让待审内容对外可见
        assertEquals(AuditStatusEnum.PENDING.getStatus(), result.auditStatus());
        ArgumentCaptor<MomentPostDO> captor = ArgumentCaptor.forClass(MomentPostDO.class);
        verify(momentPostMapper).insert(captor.capture());
        MomentPostDO saved = captor.getValue();
        assertEquals(AuditStatusEnum.PENDING.getStatus(), saved.getAuditStatus());
        assertEquals(PublishStatusEnum.SELF_ONLY.getStatus(), saved.getPublishStatus());
        assertEquals(7, saved.getTopicId());

        // 附件按 sort_no 递增落库，保证前端拿到稳定顺序
        ArgumentCaptor<MomentAttachmentDO> attachmentCaptor = ArgumentCaptor.forClass(MomentAttachmentDO.class);
        verify(momentAttachmentMapper, times(2)).insert(attachmentCaptor.capture());
        assertEquals(0, attachmentCaptor.getAllValues().get(0).getSortNo());
        assertEquals("moment/a.jpg", attachmentCaptor.getAllValues().get(0).getObjectKey());
        assertEquals(1, attachmentCaptor.getAllValues().get(1).getSortNo());

        // 内容提交事件要带出去，审核流水线靠它触发
        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertTrue(eventCaptor.getValue() instanceof MomentSubmittedEvent);
        assertEquals(MomentSubmittedEvent.TYPE_POST, ((MomentSubmittedEvent) eventCaptor.getValue()).bizType());
    }

    @Test
    void publishIsIdempotentByKey() {
        insertAssignsId(POST_ID);
        // 第一次：占位成功；第二次：直接读到首次回填的 postId
        when(valueOperations.get(anyString())).thenReturn(null, String.valueOf(POST_ID));
        when(valueOperations.setIfAbsent(anyString(), anyString(), any())).thenReturn(true);
        MomentPostDO existing = MomentPostDO.builder()
                .postId(POST_ID).userId(AUTHOR).auditStatus(0).publishStatus(0).build();
        when(momentPostMapper.selectById(POST_ID)).thenReturn(existing);

        MomentPostService.PublishCmd cmd = new MomentPostService.PublishCmd("同一条内容", List.of(), null);
        momentPostService.publish(AUTHOR, cmd, "same-key");
        MomentPostService.PublishResult second = momentPostService.publish(AUTHOR, cmd, "same-key");

        assertEquals(POST_ID, second.postId());
        // 关键：只插入一次，重放不会产生第二条动态
        verify(momentPostMapper, times(1)).insert(any(MomentPostDO.class));
    }

    @Test
    void publishRejectsFullUrlInsteadOfObjectKey() {
        ServiceException ex = assertThrows(ServiceException.class, () -> momentPostService.publish(AUTHOR,
                new MomentPostService.PublishCmd("正文", List.of("https://cdn.example.com/a.jpg"), null), "key-4"));
        assertEquals(1001, ex.getCode());
    }

    @Test
    void publishRejectsTooManyImages() {
        List<String> images = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            images.add("moment/" + i + ".jpg");
        }
        ServiceException ex = assertThrows(ServiceException.class, () -> momentPostService.publish(AUTHOR,
                new MomentPostService.PublishCmd("正文", images, null), "key-5"));
        assertEquals(1001, ex.getCode());
    }

    @Test
    void publishRejectsBlankContent() {
        ServiceException ex = assertThrows(ServiceException.class, () -> momentPostService.publish(AUTHOR,
                new MomentPostService.PublishCmd("   ", List.of(), null), "key-6"));
        assertEquals(1001, ex.getCode());
    }

    // ==================== #50 详情可见性 ====================

    @Test
    void pendingPostInvisibleToOthers() {
        when(momentPostMapper.selectById(POST_ID)).thenReturn(pendingPost());
        ServiceException ex = assertThrows(ServiceException.class,
                () -> momentPostService.detail(OTHER, POST_ID));
        assertEquals(1004, ex.getCode());
    }

    @Test
    void pendingPostVisibleToAuthor() {
        when(momentPostMapper.selectById(POST_ID)).thenReturn(pendingPost());
        when(momentCommentMapper.selectApproved(POST_ID, AUTHOR, null, 21)).thenReturn(List.of());

        MomentPostService.Detail detail = momentPostService.detail(AUTHOR, POST_ID);

        assertEquals(POST_ID, detail.post().postId());
        assertEquals(AuditStatusEnum.PENDING.getStatus(), detail.post().auditStatus());
        assertFalse(detail.commentHasMore());
    }

    @Test
    void missingPostTreatedAsNoPermission() {
        when(momentPostMapper.selectById(POST_ID)).thenReturn(null);
        ServiceException ex = assertThrows(ServiceException.class,
                () -> momentPostService.detail(OTHER, POST_ID));
        // 不存在与不可见必须同码，否则能靠错误码差异探测私密动态
        assertEquals(1004, ex.getCode());
    }

    @Test
    void sameSchoolScopeRequiresSameSchool() {
        MomentPostDO post = pendingPost();
        post.setPublishStatus(PublishStatusEnum.PUBLISHED.getStatus());
        post.setVisibleScope(1);
        when(momentPostMapper.selectById(POST_ID)).thenReturn(post);
        when(authService.getUser(OTHER)).thenReturn(user(OTHER, 10));
        when(authService.getUser(AUTHOR)).thenReturn(user(AUTHOR, 20));

        ServiceException ex = assertThrows(ServiceException.class,
                () -> momentPostService.detail(OTHER, POST_ID));
        assertEquals(1004, ex.getCode());
    }

    // ==================== #51 信息流 ====================

    @Test
    void feedRejectsUnknownType() {
        ServiceException ex = assertThrows(ServiceException.class,
                () -> momentPostService.feed(AUTHOR, "hot", null, 20));
        assertEquals(1001, ex.getCode());
    }

    @Test
    void feedTrimsToPageSizeAndExposesCursor() {
        List<MomentPostDO> rows = new ArrayList<>();
        for (long i = 0; i < 21; i++) {
            MomentPostDO post = pendingPost();
            post.setPostId(POST_ID - i);
            rows.add(post);
        }
        when(momentPostMapper.selectFeed(AUTHOR, null, null, 21)).thenReturn(rows);
        when(authService.getUser(anyLong())).thenReturn(user(AUTHOR, null));

        CursorPage<MomentPostService.PostItem> page = momentPostService.feed(AUTHOR, "recommend", null, 20);

        assertEquals(20, page.list().size());
        assertTrue(page.hasMore());
        assertEquals(String.valueOf(POST_ID - 19), page.nextCursor());
    }

    @Test
    void schoolFeedEmptyWhenViewerHasNoSchool() {
        // 未做 L3 就没有"本校"，返回空流而不是悄悄退化成推荐
        when(authService.getUser(AUTHOR)).thenReturn(user(AUTHOR, null));
        CursorPage<MomentPostService.PostItem> page = momentPostService.feed(AUTHOR, "school", null, 20);
        assertTrue(page.list().isEmpty());
        assertFalse(page.hasMore());
    }

    // ==================== #53 评论 ====================

    @Test
    void commentBelowLevel2Rejected() {
        when(authService.getAuthLevel(AUTHOR)).thenReturn(1);
        ServiceException ex = assertThrows(ServiceException.class, () -> momentPostService.comment(
                AUTHOR, POST_ID, "说两句", null, "c-key"));
        assertEquals(1003, ex.getCode());
    }

    @Test
    void commentSensitiveRejected() {
        MomentPostDO post = pendingPost();
        post.setPublishStatus(PublishStatusEnum.PUBLISHED.getStatus());
        when(momentPostMapper.selectById(POST_ID)).thenReturn(post);
        ServiceException ex = assertThrows(ServiceException.class, () -> momentPostService.comment(
                AUTHOR, POST_ID, "私聊加微信", null, "c-key"));
        assertEquals(4001, ex.getCode());
    }

    @Test
    void commentParentMustBelongToSamePost() {
        MomentPostDO post = pendingPost();
        post.setPublishStatus(PublishStatusEnum.PUBLISHED.getStatus());
        when(momentPostMapper.selectById(POST_ID)).thenReturn(post);
        MomentCommentDO parent = MomentCommentDO.builder()
                .commentId(1L).postId(1234L).parentId(null).build();
        when(momentCommentMapper.selectById(1L)).thenReturn(parent);

        ServiceException ex = assertThrows(ServiceException.class, () -> momentPostService.comment(
                AUTHOR, POST_ID, "回复", 1L, "c-key"));
        assertEquals(1001, ex.getCode());
    }

    @Test
    void commentLandsPendingAndIsReturnedToAuthor() {
        MomentPostDO post = pendingPost();
        post.setPublishStatus(PublishStatusEnum.PUBLISHED.getStatus());
        when(momentPostMapper.selectById(POST_ID)).thenReturn(post);
        when(valueOperations.get(anyString())).thenReturn(null);
        when(valueOperations.setIfAbsent(anyString(), anyString(), any())).thenReturn(true);
        doAnswer(inv -> {
            MomentCommentDO c = inv.getArgument(0);
            c.setCommentId(555L);
            c.setCreateTime(LocalDateTime.now());
            return 1;
        }).when(momentCommentMapper).insert(any(MomentCommentDO.class));
        when(authService.getUser(AUTHOR)).thenReturn(user(AUTHOR, 10));

        MomentPostService.CommentItem item = momentPostService.comment(AUTHOR, POST_ID, "一起吗", null, "c-key-2");

        assertEquals(555L, item.commentId());
        assertEquals(AuditStatusEnum.PENDING.getStatus(), item.auditStatus());
        assertNotNull(item.createTime());
        verify(eventPublisher).publishEvent(any(MomentSubmittedEvent.class));
    }

    // ==================== 辅助 ====================

    private void insertAssignsId(Long postId) {
        doAnswer(inv -> {
            MomentPostDO post = inv.getArgument(0);
            post.setPostId(postId);
            post.setCreateTime(LocalDateTime.now());
            return 1;
        }).when(momentPostMapper).insert(any(MomentPostDO.class));
    }

    private static MomentPostDO pendingPost() {
        return MomentPostDO.builder()
                .postId(POST_ID).userId(AUTHOR).content("待审内容")
                .auditStatus(AuditStatusEnum.PENDING.getStatus())
                .publishStatus(PublishStatusEnum.SELF_ONLY.getStatus())
                .visibleScope(0)
                .build();
    }

    private static SysUserDO user(Long userId, Integer schoolId) {
        return SysUserDO.builder().userId(userId).nickname("用户" + userId).schoolId(schoolId).build();
    }
}
