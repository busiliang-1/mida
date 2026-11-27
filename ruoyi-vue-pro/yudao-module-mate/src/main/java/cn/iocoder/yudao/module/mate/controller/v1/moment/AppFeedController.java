package cn.iocoder.yudao.module.mate.controller.v1.moment;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.mate.framework.auth.RequireAuthLevel;
import cn.iocoder.yudao.module.mate.framework.web.CursorResult;
import cn.iocoder.yudao.module.mate.service.moment.MomentPostService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

/**
 * M5 信息流 #51。
 * 独立于 /posts：信息流是"人看内容的入口"，动态是"内容本身"，混在一起以后加推荐位/广告位会很难切。
 */
@Tag(name = "用户端 - 信息流")
@RestController
@RequestMapping("/api/v1/feeds")
@Validated
public class AppFeedController {

    @Resource
    private MomentPostService momentPostService;

    public record FeedItemVO(Long postId, Long userId, String nickname, String avatarUrl, String content,
                             Integer topicId, List<String> images, Long likeCount, Long commentCount,
                             Long collectCount, Boolean liked, Boolean collected, LocalDateTime createTime) {
    }

    @GetMapping
    @Operation(summary = "双列信息流（recommend/follow/school，游标分页）")
    @RequireAuthLevel(1)
    public CommonResult<CursorResult<FeedItemVO>> feed(@RequestParam(required = false) String type,
                                                       @RequestParam(required = false) String cursor,
                                                       @RequestParam(required = false, defaultValue = "20") Integer size) {
        var page = momentPostService.feed(getLoginUserId(), type, cursor, size);
        List<FeedItemVO> list = page.list().stream()
                .map(p -> new FeedItemVO(p.postId(), p.userId(), p.nickname(), p.avatarUrl(), p.content(),
                        p.topicId(), p.images(), p.likeCount(), p.commentCount(), p.collectCount(),
                        p.liked(), p.collected(), p.createTime()))
                .toList();
        return success(new CursorResult<>(list, page.nextCursor(), page.hasMore()));
    }
}
