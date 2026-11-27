package cn.iocoder.yudao.module.mate.controller.v1.moment;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.mate.framework.auth.RequireAuthLevel;
import cn.iocoder.yudao.module.mate.framework.web.CursorResult;
import cn.iocoder.yudao.module.mate.service.moment.MomentInteractionService;
import cn.iocoder.yudao.module.mate.service.moment.MomentPostService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

/**
 * M5 动态接口 #49 / #50 / #52 / #53 / #54。
 * 点赞、收藏、评论都挂在这条路径下：它们的作用域就是"某条动态"，平铺出去会让鉴权找不到归属。
 */
@Tag(name = "用户端 - 动态")
@RestController
@RequestMapping("/api/v1/posts")
@Validated
public class AppMomentPostController {

    @Resource
    private MomentPostService momentPostService;
    @Resource
    private MomentInteractionService momentInteractionService;

    public record PublishReqVO(@NotBlank @Size(max = 2000) String content,
                               List<@NotBlank @Size(max = 255) String> images,
                               Integer topicId) {
    }

    public record PublishRespVO(Long postId, Integer auditStatus) {
    }

    public record CommentReqVO(@NotBlank @Size(max = 500) String content, Long parentId) {
    }

    public record CommentRespVO(Long commentId, Integer auditStatus) {
    }

    public record PostVO(Long postId, Long userId, String nickname, String avatarUrl, String content,
                         Integer topicId, List<String> images, Integer auditStatus, Integer publishStatus,
                         Integer visibleScope, Long likeCount, Long commentCount, Long collectCount,
                         Boolean liked, Boolean collected, LocalDateTime createTime) {
    }

    public record CommentVO(Long commentId, Long userId, String nickname, String content, Long parentId,
                            Integer auditStatus, LocalDateTime createTime) {
    }

    public record DetailRespVO(PostVO post, List<CommentVO> comments, String commentCursor,
                               Boolean commentHasMore) {
    }

    public record LikeRespVO(Boolean liked, Long likeCount) {
    }

    public record CollectRespVO(Boolean collected) {
    }

    @PostMapping
    @Operation(summary = "发动态（需 L2，进审核）")
    @RequireAuthLevel(2)
    public CommonResult<PublishRespVO> publish(@RequestHeader("Idempotency-Key") String idempotencyKey,
                                               @Valid @RequestBody PublishReqVO reqVO) {
        MomentPostService.PublishResult result = momentPostService.publish(getLoginUserId(),
                new MomentPostService.PublishCmd(reqVO.content(), reqVO.images(), reqVO.topicId()),
                idempotencyKey);
        return success(new PublishRespVO(result.postId(), result.auditStatus()));
    }

    @GetMapping("/{id}")
    @Operation(summary = "动态详情 + 评论首页")
    @RequireAuthLevel(1)
    public CommonResult<DetailRespVO> detail(@PathVariable("id") Long id) {
        MomentPostService.Detail detail = momentPostService.detail(getLoginUserId(), id);
        return success(new DetailRespVO(toVO(detail.post()),
                detail.comments().stream().map(AppMomentPostController::toVO).toList(),
                detail.commentCursor(), detail.commentHasMore()));
    }

    @GetMapping("/{id}/comments")
    @Operation(summary = "评论列表（游标）")
    @RequireAuthLevel(1)
    public CommonResult<CursorResult<CommentVO>> comments(@PathVariable("id") Long id,
                                                          @RequestParam(required = false) String cursor,
                                                          @RequestParam(required = false, defaultValue = "20") Integer size) {
        // #50 只给评论首页，翻页沿用同一可见性口径（不可见一律 1004）
        var page = momentPostService.commentPage(getLoginUserId(), id, cursor, size);
        return success(new CursorResult<>(page.list().stream().map(AppMomentPostController::toVO).toList(),
                page.nextCursor(), page.hasMore()));
    }

    @PostMapping("/{id}/likes")
    @Operation(summary = "点赞（需 L2，并发只 +1）")
    @RequireAuthLevel(2)
    public CommonResult<LikeRespVO> like(@PathVariable("id") Long id) {
        MomentInteractionService.LikeResult result = momentInteractionService.like(getLoginUserId(), id);
        return success(new LikeRespVO(result.liked(), result.likeCount()));
    }

    @DeleteMapping("/{id}/likes")
    @Operation(summary = "取消点赞")
    @RequireAuthLevel(2)
    public CommonResult<LikeRespVO> unlike(@PathVariable("id") Long id) {
        MomentInteractionService.LikeResult result = momentInteractionService.unlike(getLoginUserId(), id);
        return success(new LikeRespVO(result.liked(), result.likeCount()));
    }

    @PostMapping("/{id}/comments")
    @Operation(summary = "评论（需 L2，进审核）")
    @RequireAuthLevel(2)
    public CommonResult<CommentRespVO> comment(@RequestHeader("Idempotency-Key") String idempotencyKey,
                                               @PathVariable("id") Long id,
                                               @Valid @RequestBody CommentReqVO reqVO) {
        MomentPostService.CommentItem item = momentPostService.comment(getLoginUserId(), id,
                reqVO.content(), reqVO.parentId(), idempotencyKey);
        return success(new CommentRespVO(item.commentId(), item.auditStatus()));
    }

    @PostMapping("/{id}/collects")
    @Operation(summary = "收藏（需 L2）")
    @RequireAuthLevel(2)
    public CommonResult<CollectRespVO> collect(@PathVariable("id") Long id) {
        return success(new CollectRespVO(momentInteractionService.collect(getLoginUserId(), id).collected()));
    }

    @DeleteMapping("/{id}/collects")
    @Operation(summary = "取消收藏")
    @RequireAuthLevel(2)
    public CommonResult<CollectRespVO> uncollect(@PathVariable("id") Long id) {
        return success(new CollectRespVO(momentInteractionService.uncollect(getLoginUserId(), id).collected()));
    }

    private static PostVO toVO(MomentPostService.PostItem item) {
        return new PostVO(item.postId(), item.userId(), item.nickname(), item.avatarUrl(), item.content(),
                item.topicId(), item.images(), item.auditStatus(), item.publishStatus(), item.visibleScope(),
                item.likeCount(), item.commentCount(), item.collectCount(), item.liked(), item.collected(),
                item.createTime());
    }

    private static CommentVO toVO(MomentPostService.CommentItem item) {
        return new CommentVO(item.commentId(), item.userId(), item.nickname(), item.content(),
                item.parentId(), item.auditStatus(), item.createTime());
    }
}
