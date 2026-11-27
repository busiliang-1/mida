package cn.iocoder.yudao.module.mate.service.moment;

import cn.iocoder.yudao.module.mate.framework.web.CursorPage;

import java.time.LocalDateTime;
import java.util.List;

/**
 * M5 动态与信息流 Service（#49 / #50 / #51 / #53）。
 * 先审后发：新建动态 audit_status=0 + publish_status=0，审核通过才 publish_status=1。
 * 所以"待审内容不对外"这件事只由 publish_status 一个字段决定，信息流 SQL 不必再判断审核态。
 */
public interface MomentPostService {

    record PublishCmd(String content, List<String> images, Integer topicId) {
    }

    record PublishResult(Long postId, Integer auditStatus) {
    }

    /**
     * 信息流单项。counts/liked/collected 由批量查询回填，
     * 不允许在渲染循环里逐条 COUNT —— 20 条就会打 60 次 DB。
     */
    record PostItem(Long postId, Long userId, String nickname, String avatarUrl, String content, Integer topicId,
                    List<String> images, Integer auditStatus, Integer publishStatus, Integer visibleScope,
                    long likeCount, long commentCount, long collectCount, boolean liked, boolean collected,
                    LocalDateTime createTime) {
    }

    record CommentItem(Long commentId, Long userId, String nickname, String content, Long parentId,
                       Integer auditStatus, LocalDateTime createTime) {
    }

    record Detail(PostItem post, List<CommentItem> comments, String commentCursor, boolean commentHasMore) {
    }

    /** 发动态（需 L2） */
    PublishResult publish(Long userId, PublishCmd cmd, String idempotencyKey);

    /** 动态详情 + 评论首页；不可见/不存在统一 1004 */
    Detail detail(Long viewerId, Long postId);

    /**
     * 信息流。type：recommend（默认）/ follow / school。
     */
    CursorPage<PostItem> feed(Long viewerId, String type, String cursor, int size);

    /** 评论（需 L2），进审核 */
    CommentItem comment(Long userId, Long postId, String content, Long parentId, String idempotencyKey);

    /** 评论列表（游标），只含已通过 + viewer 自己的待审评论 */
    CursorPage<CommentItem> commentPage(Long viewerId, Long postId, String cursor, int size);

    /** 断言动态对 viewer 可见，返回 DO；不可见抛 1004。供互动接口复用 */
    void assertVisible(Long viewerId, Long postId);

    /** 取某人的发布列表（主页用）；onlyPublished=true 时只给已发布 */
    List<PostItem> listByUser(Long viewerId, Long userId, boolean onlyPublished, int limit);
}
