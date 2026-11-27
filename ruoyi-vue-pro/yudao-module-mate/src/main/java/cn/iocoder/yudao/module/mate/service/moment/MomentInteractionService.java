package cn.iocoder.yudao.module.mate.service.moment;

/**
 * M5 互动 Service（#52 点赞 / #54 收藏 / #55 关注）。
 *
 * <p>三者语义一致：动作幂等 + 唯一键防重。点赞/收藏重复调用返回 liked=true 而不是报错，
 * 取关不存在的关注也不报错 —— 前端重试、双端并发点击都不该看到 5xx。
 */
public interface MomentInteractionService {

    record LikeResult(boolean liked, long likeCount) {
    }

    record CollectResult(boolean collected) {
    }

    record FollowResult(boolean followed) {
    }

    LikeResult like(Long userId, Long postId);

    LikeResult unlike(Long userId, Long postId);

    CollectResult collect(Long userId, Long postId);

    CollectResult uncollect(Long userId, Long postId);

    FollowResult follow(Long followerId, Long followeeId);

    FollowResult unfollow(Long followerId, Long followeeId);

    boolean isCollected(Long userId, Long postId);
}
