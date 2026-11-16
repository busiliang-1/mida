package cn.iocoder.yudao.module.mate.controller.admin.auth;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.mate.dal.dataobject.auth.AuthRecordDO;
import cn.iocoder.yudao.module.mate.dal.mysql.auth.AuthRecordMapper;
import cn.iocoder.yudao.module.mate.service.auth.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/**
 * 后台认证审核接口。包路径 controller.admin → 自动加 /admin-api 前缀。
 */
@Tag(name = "管理后台 - 认证审核")
@RestController
@RequestMapping("/mate/auth-record")
@Validated
public class AdminAuthRecordController {

    @Resource
    private AuthService authService;
    @Resource
    private AuthRecordMapper authRecordMapper;

    public record ReviewReqVO(@NotNull Long recordId, @NotNull Integer action, String reason, Integer schoolId) {}

    @GetMapping("/page")
    @Operation(summary = "认证审核队列")
    @PreAuthorize("@ss.hasPermission('mate:auth-record:query')")
    public CommonResult<PageResult<AuthRecordDO>> page(@Valid PageParam pageParam,
                                                       @RequestParam(value = "verifyResult", required = false) Integer verifyResult) {
        return success(authRecordMapper.selectPage(pageParam, verifyResult));
    }

    @PutMapping("/review")
    @Operation(summary = "审核认证记录(通过/驳回)")
    @PreAuthorize("@ss.hasPermission('mate:auth-record:review')")
    public CommonResult<Boolean> review(@Valid @RequestBody ReviewReqVO reqVO) {
        String operator = SecurityFrameworkUtils.getLoginUserNickname();
        authService.reviewAuthRecord(reqVO.recordId(), reqVO.action(), operator, reqVO.schoolId());
        return success(true);
    }
}
