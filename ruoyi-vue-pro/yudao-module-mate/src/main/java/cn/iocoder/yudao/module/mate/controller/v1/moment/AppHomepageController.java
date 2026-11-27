package cn.iocoder.yudao.module.mate.controller.v1.moment;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.mate.framework.auth.RequireAuthLevel;
import cn.iocoder.yudao.module.mate.service.goods.GoodsService;
import cn.iocoder.yudao.module.mate.service.moment.HomepageService;
import cn.iocoder.yudao.module.mate.service.moment.MomentPostService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

/**
 * M5 主页 #56 / #57。
 * 两个接口路径分属不同前缀（/users/{id}/homepage 与 /homepages/config），
 * 类上只挂公共的 /api/v1，避免为了凑一个 @RequestMapping 而改写契约路径。
 */
@Tag(name = "用户端 - 主页")
@RestController
@RequestMapping("/api/v1")
@Validated
public class AppHomepageController {

    @Resource
    private HomepageService homepageService;

    public record ConfigVO(String coverKey, String signature, Map<String, Object> modulesJson) {
    }

    public record PostVO(Long postId, String content, List<String> images, Integer auditStatus,
                         Integer publishStatus, Long likeCount, Long commentCount, LocalDateTime createTime) {
    }

    public record GoodsVO(Long goodsId, String title, String category, BigDecimal price,
                          List<String> images, Integer status) {
    }

    public record HomepageVO(Long userId, String nickname, String avatarUrl, Integer authLevel, Integer schoolId,
                             Long followingCount, Long followerCount, Boolean following,
                             ConfigVO config, List<PostVO> posts, List<GoodsVO> goods) {
    }

    public record UpdateReqVO(@Size(max = 255) String coverKey,
                              @Size(max = 100) String signature,
                              Map<String, Object> modulesJson) {
    }

    public record UpdateRespVO(Boolean updated) {
    }

    @GetMapping("/users/{id}/homepage")
    @Operation(summary = "主页渲染数据（配置 + 发布 + 二手）")
    @RequireAuthLevel(1)
    public CommonResult<HomepageVO> homepage(@PathVariable("id") Long id) {
        HomepageService.Homepage homepage = homepageService.get(getLoginUserId(), id);
        List<PostVO> posts = homepage.posts().stream()
                .map(p -> new PostVO(p.postId(), p.content(), p.images(), p.auditStatus(),
                        p.publishStatus(), p.likeCount(), p.commentCount(), p.createTime()))
                .toList();
        List<GoodsVO> goods = homepage.goods().stream()
                .map(g -> new GoodsVO(g.goodsId(), g.title(), g.category(), g.price(), g.images(), g.status()))
                .toList();
        ConfigVO config = new ConfigVO(homepage.config().coverKey(), homepage.config().signature(),
                homepage.config().modulesJson());
        return success(new HomepageVO(homepage.userId(), homepage.nickname(), homepage.avatarUrl(),
                homepage.authLevel(), homepage.schoolId(), homepage.followingCount(), homepage.followerCount(),
                homepage.following(), config, posts, goods));
    }

    @PutMapping("/homepages/config")
    @Operation(summary = "更新主页配置（需 L2）")
    @RequireAuthLevel(2)
    public CommonResult<UpdateRespVO> updateConfig(@Valid @RequestBody UpdateReqVO reqVO) {
        homepageService.updateConfig(getLoginUserId(), new HomepageService.UpdateCmd(
                reqVO.coverKey(), reqVO.signature(), reqVO.modulesJson()));
        return success(new UpdateRespVO(true));
    }
}
