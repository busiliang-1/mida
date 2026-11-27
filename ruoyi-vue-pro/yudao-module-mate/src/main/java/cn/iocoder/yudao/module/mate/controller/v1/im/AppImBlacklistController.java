package cn.iocoder.yudao.module.mate.controller.v1.im;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.mate.framework.auth.RequireAuthLevel;
import cn.iocoder.yudao.module.mate.service.im.ImBlacklistService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.module.mate.enums.ErrorCodeConstants.UNAUTHORIZED;

/**
 * M3 拉黑接口 #31（另有 DELETE 解除）。
 */
@Tag(name = "用户端 - IM 拉黑")
@RestController
@RequestMapping("/api/v1/blacklists")
@Validated
public class AppImBlacklistController {

    @Resource
    private ImBlacklistService blacklistService;

    public record BlockReqVO(@NotNull Long blockedUserId) {
    }

    public record BlockRespVO(boolean blocked) {
    }

    @PostMapping
    @Operation(summary = "拉黑（双向阻断）")
    @RequireAuthLevel(2)
    public CommonResult<BlockRespVO> block(@Valid @RequestBody BlockReqVO reqVO) {
        return success(new BlockRespVO(blacklistService.block(requireLogin(), reqVO.blockedUserId())));
    }

    @DeleteMapping("/{userId}")
    @Operation(summary = "解除拉黑")
    @RequireAuthLevel(2)
    public CommonResult<BlockRespVO> unblock(@PathVariable("userId") Long userId) {
        return success(new BlockRespVO(!blacklistService.unblock(requireLogin(), userId)));
    }

    private Long requireLogin() {
        Long userId = SecurityFrameworkUtils.getLoginUserId();
        if (userId == null) {
            throw exception(UNAUTHORIZED);
        }
        return userId;
    }
}
