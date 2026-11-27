package cn.iocoder.yudao.module.mate.controller.v1.moment;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.mate.framework.auth.RequireAuthLevel;
import cn.iocoder.yudao.module.mate.service.moment.MomentInteractionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

/**
 * M5 关注 #55。
 */
@Tag(name = "用户端 - 关注")
@RestController
@RequestMapping("/api/v1/users")
@Validated
public class AppUserFollowController {

    @Resource
    private MomentInteractionService momentInteractionService;

    public record FollowRespVO(Boolean followed) {
    }

    @PostMapping("/{id}/follows")
    @Operation(summary = "关注（需 L2）")
    @RequireAuthLevel(2)
    public CommonResult<FollowRespVO> follow(@PathVariable("id") Long id) {
        return success(new FollowRespVO(momentInteractionService.follow(getLoginUserId(), id).followed()));
    }

    @DeleteMapping("/{id}/follows")
    @Operation(summary = "取关")
    @RequireAuthLevel(2)
    public CommonResult<FollowRespVO> unfollow(@PathVariable("id") Long id) {
        return success(new FollowRespVO(momentInteractionService.unfollow(getLoginUserId(), id).followed()));
    }
}
