package cn.iocoder.yudao.module.mate.controller.v1.im;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.mate.service.im.ImCardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.annotation.security.PermitAll;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.module.mate.enums.ErrorCodeConstants.UNAUTHORIZED;

/**
 * 内部接口 #32：IM 发送前校验单据归属与状态。
 * 无用户令牌，用共享密钥头 X-Internal-Token 保护；无副作用，可被重试。
 */
@Tag(name = "内部 - IM 发送前校验")
@RestController
@RequestMapping("/internal/v1/im")
@Validated
public class InternalImController {

    @Resource
    private ImCardService cardService;

    @Value("${yudao.mate.internal-token:mida-internal-dev-token}")
    private String internalToken;

    public record CallbackReqVO(@NotBlank String bizNo, @NotNull Integer cardType, @NotNull Long senderId) {
    }

    public record CallbackRespVO(boolean allow) {
    }

    @PostMapping("/callback")
    @Operation(summary = "发送前校验（allow）")
    @PermitAll
    public CommonResult<CallbackRespVO> callback(@RequestHeader(value = "X-Internal-Token", required = false) String token,
                                                 @Valid @RequestBody CallbackReqVO reqVO) {
        if (token == null || !token.equals(internalToken)) {
            throw exception(UNAUTHORIZED);
        }
        // 校验失败直接抛 1004，与 #32 的错误码定义一致
        cardService.validateOrThrow(reqVO.cardType(), reqVO.bizNo(), reqVO.senderId());
        return success(new CallbackRespVO(true));
    }
}
