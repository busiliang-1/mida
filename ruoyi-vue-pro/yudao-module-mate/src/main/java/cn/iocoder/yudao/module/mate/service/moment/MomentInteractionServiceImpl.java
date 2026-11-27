package cn.iocoder.yudao.module.mate.service.moment;

import cn.iocoder.yudao.module.mate.dal.dataobject.auth.SysUserDO;
import cn.iocoder.yudao.module.mate.dal.dataobject.moment.MomentCollectDO;
import cn.iocoder.yudao.module.mate.dal.dataobject.moment.MomentLikeDO;
import cn.iocoder.yudao.module.mate.dal.dataobject.moment.UserFollowDO;
import cn.iocoder.yudao.module.mate.dal.mysql.moment.MomentCollectMapper;
import cn.iocoder.yudao.module.mate.dal.mysql.moment.MomentLikeMapper;
import cn.iocoder.yudao.module.mate.dal.mysql.moment.UserFollowMapper;
import cn.iocoder.yudao.module.mate.service.auth.AuthService;
import jakarta.annotation.Resource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.util.Objects;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.mate.enums.ErrorCodeConstants.AUTH_LEVEL_INSUFFICIENT;
import static cn.iocoder.yudao.module.mate.enums.ErrorCodeConstants.RESOURCE_OWNER_MISMATCH;

/**
 * M5 互动实现。
 * 并发口径：不做"先查再插"，直接 INSERT 让唯一键裁决，撞键即视为已点赞/已收藏，返回幂等结果。
 * 这样两个并发请求只有一次写入成功，计数不会被加两次；也不会因为查-插之间的窗口漏判。
 */
@Service
@Validated
public class MomentInteractionServiceImpl implements MomentInteractionService {

    @Resource
    private MomentLikeMapper momentLikeMapper;
    @Resource
    private MomentCollectMapper momentCollectMapper;
    @Resource
    private UserFollowMapper userFollowMapper;
    @Resource
    private MomentPostService momentPostService;
    @Resource
    private MomentCounter momentCounter;
    @Resource
    private AuthService authService;

    @Override
    public LikeResult like(Long userId, Long postId) {
        assertLevel(userId, 2);
        momentPostService.assertVisible(userId, postId);
        try {
            momentLikeMapper.insert(MomentLikeDO.builder().postId(postId).userId(userId).build());
            momentCounter.onLikeChanged(postId, 1);
        } catch (DuplicateKeyException ignored) {
            // 已点过赞：幂等成功，不重复计数
        }
        return new LikeResult(true, momentCounter.likeCount(postId));
    }

    @Override
    public LikeResult unlike(Long userId, Long postId) {
        assertLevel(userId, 2);
        momentPostService.assertVisible(userId, postId);
        if (momentLikeMapper.deleteByPostAndUser(postId, userId) > 0) {
            momentCounter.onLikeChanged(postId, -1);
        }
        return new LikeResult(false, momentCounter.likeCount(postId));
    }

    @Override
    public CollectResult collect(Long userId, Long postId) {
        assertLevel(userId, 2);
        momentPostService.assertVisible(userId, postId);
        try {
            momentCollectMapper.insert(MomentCollectDO.builder().postId(postId).userId(userId).build());
        } catch (DuplicateKeyException ignored) {
            // 已收藏：幂等成功
        }
        return new CollectResult(true);
    }

    @Override
    public CollectResult uncollect(Long userId, Long postId) {
        assertLevel(userId, 2);
        momentCollectMapper.deleteByPostAndUser(postId, userId);
        return new CollectResult(false);
    }

    @Override
    public FollowResult follow(Long followerId, Long followeeId) {
        assertLevel(followerId, 2);
        if (Objects.equals(followerId, followeeId)) {
            // 自己关注自己没有业务含义，直接拒绝，避免主页数据出现"自己关注自己"
            throw exception(RESOURCE_OWNER_MISMATCH);
        }
        SysUserDO target = authService.getUser(followeeId);
        if (target == null) {
            throw exception(RESOURCE_OWNER_MISMATCH);
        }
        try {
            userFollowMapper.insert(UserFollowDO.builder()
                    .followerId(followerId).followeeId(followeeId).build());
        } catch (DuplicateKeyException ignored) {
            // 已关注：幂等成功
        }
        return new FollowResult(true);
    }

    @Override
    public FollowResult unfollow(Long followerId, Long followeeId) {
        assertLevel(followerId, 2);
        userFollowMapper.deleteByFollowerAndFollowee(followerId, followeeId);
        return new FollowResult(false);
    }

    @Override
    public boolean isCollected(Long userId, Long postId) {
        return momentCollectMapper.selectByPostAndUser(postId, userId) != null;
    }

    private void assertLevel(Long userId, int required) {
        Integer level = authService.getAuthLevel(userId);
        if (level == null || level < required) {
            throw exception(AUTH_LEVEL_INSUFFICIENT);
        }
    }
}
