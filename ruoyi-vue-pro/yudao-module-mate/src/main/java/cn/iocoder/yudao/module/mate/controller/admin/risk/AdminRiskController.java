package cn.iocoder.yudao.module.mate.controller.admin.risk;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.mate.dal.dataobject.risk.RiskEventDO;
import cn.iocoder.yudao.module.mate.dal.dataobject.risk.RiskReportDO;
import cn.iocoder.yudao.module.mate.dal.mysql.risk.RiskEventMapper;
import cn.iocoder.yudao.module.mate.service.risk.ReportService;
import cn.iocoder.yudao.module.mate.service.risk.RiskService;
import cn.iocoder.yudao.module.mate.service.risk.SensitiveWordService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 风控")
@RestController
@RequestMapping("/mate/risk")
@Validated
public class AdminRiskController {

    @Resource
    private ReportService reportService;
    @Resource
    private RiskService riskService;
    @Resource
    private SensitiveWordService sensitiveWordService;
    @Resource
    private RiskEventMapper riskEventMapper;

    public record ReviewReqVO(@NotNull Long reportId, @NotNull Integer action) {}
    public record BlacklistReqVO(@NotNull Long userId, Integer type, String reason) {}
    public record AuditCheckReqVO(String text) {}
    public record AddWordReqVO(@NotNull String word) {}
    public record ScoreReqVO(@NotNull Long userId, Integer scene, BigDecimal amount) {}

    @GetMapping("/report/page")
    @Operation(summary = "举报工单队列")
    @PreAuthorize("@ss.hasPermission('mate:risk:query')")
    public CommonResult<PageResult<RiskReportDO>> reportPage(@Valid PageParam pageParam,
                                                             @RequestParam(required = false) Integer status) {
        return success(reportService.pageReport(pageParam, status));
    }

    @PutMapping("/report/review")
    @Operation(summary = "处置举报")
    @PreAuthorize("@ss.hasPermission('mate:risk:review')")
    public CommonResult<Boolean> review(@Valid @RequestBody ReviewReqVO reqVO) {
        reportService.reviewReport(reqVO.reportId(), reqVO.action(), SecurityFrameworkUtils.getLoginUserNickname());
        return success(true);
    }

    @PostMapping("/blacklist/add")
    @Operation(summary = "加入黑名单")
    @PreAuthorize("@ss.hasPermission('mate:risk:blacklist')")
    public CommonResult<Boolean> addBlacklist(@Valid @RequestBody BlacklistReqVO reqVO) {
        reportService.addBlacklist(reqVO.userId(), reqVO.type() == null ? 3 : reqVO.type(), reqVO.reason());
        return success(true);
    }

    @DeleteMapping("/blacklist/remove")
    @Operation(summary = "移出黑名单")
    @PreAuthorize("@ss.hasPermission('mate:risk:blacklist')")
    public CommonResult<Boolean> removeBlacklist(@RequestParam Long userId) {
        reportService.removeBlacklist(userId);
        return success(true);
    }

    @GetMapping("/event/list")
    @Operation(summary = "风险事件审计")
    @PreAuthorize("@ss.hasPermission('mate:risk:query')")
    public CommonResult<List<RiskEventDO>> eventList(@RequestParam Long userId) {
        return success(riskEventMapper.selectListByUser(userId));
    }

    @GetMapping("/audit/dict-version")
    @Operation(summary = "敏感词库版本")
    public CommonResult<Map<String, Object>> dictVersion() {
        return success(Map.of("version", sensitiveWordService.getDictVersion(),
                "words", sensitiveWordService.getWordCount()));
    }

    @PostMapping("/audit/check")
    @Operation(summary = "敏感词命中测试")
    public CommonResult<SensitiveWordService.AuditResult> auditCheck(@RequestBody AuditCheckReqVO reqVO) {
        return success(sensitiveWordService.match(reqVO.text()));
    }

    @PostMapping("/audit/reload")
    @Operation(summary = "热加载新增敏感词")
    @PreAuthorize("@ss.hasPermission('mate:risk:audit')")
    public CommonResult<Integer> addWord(@Valid @RequestBody AddWordReqVO reqVO) {
        sensitiveWordService.addWord(reqVO.word());
        return success(sensitiveWordService.getDictVersion());
    }

    @PostMapping("/score")
    @Operation(summary = "风险评分(供转账准入/测试)")
    public CommonResult<RiskService.RiskResult> score(@Valid @RequestBody ScoreReqVO reqVO) {
        return success(riskService.evaluate(reqVO.userId(), reqVO.scene(), reqVO.amount()));
    }
}
