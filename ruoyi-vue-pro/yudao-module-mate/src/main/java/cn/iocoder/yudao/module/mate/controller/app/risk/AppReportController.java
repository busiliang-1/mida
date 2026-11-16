package cn.iocoder.yudao.module.mate.controller.app.risk;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.mate.framework.auth.RequireAuthLevel;
import cn.iocoder.yudao.module.mate.service.risk.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "用户端 - 举报")
@RestController
@RequestMapping("/mate/report")
@Validated
public class AppReportController {

    @Resource
    private ReportService reportService;

    public record ReportReqVO(@NotNull Integer targetType, @NotNull Long targetId,
                              @NotNull Integer reason, String evidenceRef) {}

    @PostMapping("/create")
    @Operation(summary = "提交举报")
    @RequireAuthLevel(2)
    public CommonResult<Long> create(@Valid @RequestBody ReportReqVO reqVO) {
        return success(reportService.createReport(SecurityFrameworkUtils.getLoginUserId(),
                reqVO.targetType(), reqVO.targetId(), reqVO.reason(), reqVO.evidenceRef()));
    }
}
