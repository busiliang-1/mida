package cn.iocoder.yudao.module.mate.controller.app.auth;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.mate.dal.dataobject.auth.SysUserDO;
import cn.iocoder.yudao.module.mate.framework.auth.RequireAuthLevel;
import cn.iocoder.yudao.module.mate.service.auth.AuthService;
import cn.iocoder.yudao.module.system.dal.dataobject.oauth2.OAuth2AccessTokenDO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.annotation.security.PermitAll;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/**
 * C 端用户认证接口。包路径 controller.app → 自动加 /app-api 前缀。
 */
@Tag(name = "用户端 - 认证")
@RestController
@RequestMapping("/mate/auth")
@Validated
public class AppAuthController {

    @Resource
    private AuthService authService;

    public record SmsReqVO(@NotBlank String phone) {}
    public record RegisterReqVO(@NotBlank String phone, @NotBlank String code, String nickname) {}
    public record LoginReqVO(@NotBlank String phone, @NotBlank String code) {}
    public record VerificationReqVO(@NotNull Integer authType, String materialDigest,
                                    String reportNo, String realName, String idCard) {}
    public record TokenRespVO(Long userId, String accessToken, String refreshToken, java.time.LocalDateTime expiresTime) {}
    public record ProfileRespVO(Long userId, String nickname, Integer authLevel, Integer schoolId,
                                Integer creditScore, Integer gender, String avatarUrl) {}

    @PostMapping("/sms-codes")
    @Operation(summary = "发送验证码")
    @PermitAll
    public CommonResult<Boolean> sendSmsCode(@Valid @RequestBody SmsReqVO reqVO) {
        authService.sendSmsCode(reqVO.phone());
        return success(true);
    }

    @PostMapping("/registrations")
    @Operation(summary = "手机号注册(L1)")
    @PermitAll
    public CommonResult<Long> register(@Valid @RequestBody RegisterReqVO reqVO) {
        return success(authService.register(reqVO.phone(), reqVO.code(), reqVO.nickname()));
    }

    @PostMapping("/sessions")
    @Operation(summary = "登录签发令牌")
    @PermitAll
    public CommonResult<TokenRespVO> login(@Valid @RequestBody LoginReqVO reqVO) {
        OAuth2AccessTokenDO token = authService.login(reqVO.phone(), reqVO.code());
        return success(new TokenRespVO(token.getUserId(), token.getAccessToken(),
                token.getRefreshToken(), token.getExpiresTime()));
    }

    @PostMapping("/verifications")
    @Operation(summary = "提交实名(L2)/学校(L3)认证")
    @RequireAuthLevel(1)
    public CommonResult<Long> submitVerification(@Valid @RequestBody VerificationReqVO reqVO) {
        Long userId = SecurityFrameworkUtils.getLoginUserId();
        return success(authService.submitVerification(userId, reqVO.authType(), reqVO.materialDigest(),
                reqVO.reportNo(), reqVO.realName(), reqVO.idCard()));
    }

    @GetMapping("/profile")
    @Operation(summary = "本人资料与等级")
    @RequireAuthLevel(1)
    public CommonResult<ProfileRespVO> profile() {
        SysUserDO user = authService.getUser(SecurityFrameworkUtils.getLoginUserId());
        return success(new ProfileRespVO(user.getUserId(), user.getNickname(), user.getAuthLevel(),
                user.getSchoolId(), user.getCreditScore(), user.getGender(), user.getAvatarUrl()));
    }
}
