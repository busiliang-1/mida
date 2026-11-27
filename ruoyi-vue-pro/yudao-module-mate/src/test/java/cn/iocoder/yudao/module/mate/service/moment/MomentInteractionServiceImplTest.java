package cn.iocoder.yudao.module.mate.service.moment;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.mate.dal.dataobject.auth.SysUserDO;
import cn.iocoder.yudao.module.mate.dal.dataobject.moment.MomentCollectDO;
import cn.iocoder.yudao.module.mate.dal.dataobject.moment.MomentLikeDO;
import cn.iocoder.yudao.module.mate.dal.dataobject.moment.UserFollowDO;
import cn.iocoder.yudao.module.mate.dal.mysql.moment.MomentCollectMapper;
import cn.iocoder.yudao.module.mate.dal.mysql.moment.MomentLikeMapper;
import cn.iocoder.yudao.module.mate.dal.mysql.moment.UserFollowMapper;
import cn.iocoder.yudao.module.mate.service.auth.AuthService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * M5 互动（点赞 / 收藏 / 关注）测试。
 *
 * 核心口径：并发重复点赞只 +1 —— 靠唯一键裁决而不是"先查再插"，
 * 撞键视为已点赞并返回幂等成功，且不重复触发计数变更。
 */
@ExtendWith(MockitoExtension.class)
class MomentInteractionServiceImplTest {

    private static final Long USER = 1L;
    private static final Long TARGET = 2L;
    private static final Long POST_ID = 9001L;

    @Mock
    private MomentLikeMapper momentLikeMapper;
    @Mock
    private MomentCollectMapper momentCollectMapper;
    @Mock
    private UserFollowMapper userFollowMapper;
    @Mock
    private MomentPostService momentPostService;
    @Mock
    private MomentCounter momentCounter;
    @Mock
    private AuthService authService;

    @InjectMocks
    private MomentInteractionServiceImpl interactionService;

    @Test
    void likeCountsOnceAndReturnsCount() {
        when(authService.getAuthLevel(USER)).thenReturn(2);
        when(momentCounter.likeCount(POST_ID)).thenReturn(1L);

        MomentInteractionService.LikeResult result = interactionService.like(USER, POST_ID);

        assertTrue(result.liked());
        assertEquals(1L, result.likeCount());
        verify(momentLikeMapper).insert(any(MomentLikeDO.class));
        verify(momentCounter).onLikeChanged(POST_ID, 1L);
    }

    @Test
    void duplicateLikeIsIdempotentAndDoesNotDoubleCount() {
        when(authService.getAuthLevel(USER)).thenReturn(2);
        when(momentLikeMapper.insert(any(MomentLikeDO.class))).thenThrow(new DuplicateKeyException("uk_post_user"));
        when(momentCounter.likeCount(POST_ID)).thenReturn(7L);

        MomentInteractionService.LikeResult result = interactionService.like(USER, POST_ID);

        // 并发/重放都返回成功，但计数不被二次抬高
        assertTrue(result.liked());
        assertEquals(7L, result.likeCount());
        verify(momentCounter, never()).onLikeChanged(anyLong(), anyLong());
    }

    @Test
    void likeBelowLevel2Rejected() {
        when(authService.getAuthLevel(USER)).thenReturn(1);
        ServiceException ex = assertThrows(ServiceException.class, () -> interactionService.like(USER, POST_ID));
        assertEquals(1003, ex.getCode());
        verify(momentLikeMapper, never()).insert(any(MomentLikeDO.class));
    }

    @Test
    void unlikeOnlyDecaysWhenRowActuallyDeleted() {
        when(authService.getAuthLevel(USER)).thenReturn(2);
        when(momentLikeMapper.deleteByPostAndUser(POST_ID, USER)).thenReturn(1);
        when(momentCounter.likeCount(POST_ID)).thenReturn(0L);

        MomentInteractionService.LikeResult result = interactionService.unlike(USER, POST_ID);

        assertFalse(result.liked());
        verify(momentCounter).onLikeChanged(POST_ID, -1L);
    }

    @Test
    void unlikeMissingRowIsNoop() {
        when(authService.getAuthLevel(USER)).thenReturn(2);
        when(momentLikeMapper.deleteByPostAndUser(POST_ID, USER)).thenReturn(0);
        when(momentCounter.likeCount(POST_ID)).thenReturn(0L);

        interactionService.unlike(USER, POST_ID);

        // 没删到行说明本来就没点过赞，不能把计数减掉
        verify(momentCounter, never()).onLikeChanged(anyLong(), anyLong());
    }

    @Test
    void collectTwiceIsIdempotent() {
        when(authService.getAuthLevel(USER)).thenReturn(2);
        when(momentCollectMapper.insert(any(MomentCollectDO.class)))
                .thenThrow(new DuplicateKeyException("uk_post_user"));

        MomentInteractionService.CollectResult result = interactionService.collect(USER, POST_ID);

        assertTrue(result.collected());
    }

    @Test
    void followSelfRejected() {
        when(authService.getAuthLevel(USER)).thenReturn(2);
        ServiceException ex = assertThrows(ServiceException.class, () -> interactionService.follow(USER, USER));
        assertEquals(1004, ex.getCode());
        verify(userFollowMapper, never()).insert(any(UserFollowDO.class));
    }

    @Test
    void followMissingUserRejected() {
        when(authService.getAuthLevel(USER)).thenReturn(2);
        when(authService.getUser(TARGET)).thenReturn(null);
        ServiceException ex = assertThrows(ServiceException.class, () -> interactionService.follow(USER, TARGET));
        assertEquals(1004, ex.getCode());
    }

    @Test
    void followDuplicateStaysIdempotent() {
        lenient().when(authService.getAuthLevel(USER)).thenReturn(2);
        when(authService.getUser(TARGET)).thenReturn(SysUserDO.builder().userId(TARGET).build());
        when(userFollowMapper.insert(any(UserFollowDO.class))).thenThrow(new DuplicateKeyException("uk"));

        assertTrue(interactionService.follow(USER, TARGET).followed());
    }

    @Test
    void unfollowIsIdempotent() {
        when(authService.getAuthLevel(USER)).thenReturn(2);
        when(userFollowMapper.deleteByFollowerAndFollowee(USER, TARGET)).thenReturn(0);

        assertFalse(interactionService.unfollow(USER, TARGET).followed());
    }
}
