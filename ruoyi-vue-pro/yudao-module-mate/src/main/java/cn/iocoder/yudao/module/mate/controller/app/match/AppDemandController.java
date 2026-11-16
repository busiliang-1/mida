package cn.iocoder.yudao.module.mate.controller.app.match;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.mate.dal.dataobject.match.MateDemandDO;
import cn.iocoder.yudao.module.mate.dal.dataobject.match.MateSignupDO;
import cn.iocoder.yudao.module.mate.framework.auth.RequireAuthLevel;
import cn.iocoder.yudao.module.mate.service.match.DemandService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "用户端 - 搭子需求")
@RestController
@RequestMapping("/mate/demand")
@Validated
public class AppDemandController {

    @Resource
    private DemandService demandService;

    public record CreateDemandReqVO(@NotBlank String title, @NotBlank String activityType,
                                    @NotNull LocalDateTime startTime, LocalDateTime endTime,
                                    @NotBlank String locationName, BigDecimal longitude, BigDecimal latitude,
                                    @NotBlank String cityCode, @NotNull Integer needCount,
                                    Integer genderLimit, Integer authLimit, Integer feeMode, String description) {}
    public record SignupReqVO(String message) {}
    public record ReviewReqVO(@NotNull Integer action) {}

    @PostMapping("/create")
    @Operation(summary = "发布需求单")
    @RequireAuthLevel(2)
    public CommonResult<Long> create(@Valid @RequestBody CreateDemandReqVO reqVO) {
        MateDemandDO demand = MateDemandDO.builder()
                .title(reqVO.title()).activityType(reqVO.activityType())
                .startTime(reqVO.startTime()).endTime(reqVO.endTime())
                .locationName(reqVO.locationName()).longitude(reqVO.longitude()).latitude(reqVO.latitude())
                .cityCode(reqVO.cityCode()).needCount(reqVO.needCount())
                .genderLimit(reqVO.genderLimit() == null ? 0 : reqVO.genderLimit())
                .authLimit(reqVO.authLimit() == null ? 1 : reqVO.authLimit())
                .feeMode(reqVO.feeMode() == null ? 0 : reqVO.feeMode())
                .description(reqVO.description())
                .build();
        return success(demandService.createDemand(SecurityFrameworkUtils.getLoginUserId(), demand));
    }

    @GetMapping("/page")
    @Operation(summary = "需求广场")
    public CommonResult<PageResult<MateDemandDO>> page(@Valid PageParam pageParam,
                                                       @RequestParam(required = false) String cityCode,
                                                       @RequestParam(required = false) String activityType,
                                                       @RequestParam(required = false) Integer status) {
        return success(demandService.pageDemand(pageParam, cityCode, activityType, status));
    }

    @GetMapping("/get")
    @Operation(summary = "需求详情")
    public CommonResult<MateDemandDO> get(@RequestParam("id") Long id) {
        return success(demandService.getDemand(id));
    }

    @PostMapping("/{id}/signups")
    @Operation(summary = "报名")
    @RequireAuthLevel(2)
    public CommonResult<Long> signup(@PathVariable("id") Long id, @RequestBody(required = false) SignupReqVO reqVO) {
        String message = reqVO == null ? null : reqVO.message();
        return success(demandService.signup(SecurityFrameworkUtils.getLoginUserId(), id, message));
    }

    @PatchMapping("/{id}/signups/{signupId}")
    @Operation(summary = "审核报名(触发成团)")
    @RequireAuthLevel(2)
    public CommonResult<Boolean> review(@PathVariable("id") Long id, @PathVariable("signupId") Long signupId,
                                        @Valid @RequestBody ReviewReqVO reqVO) {
        demandService.reviewSignup(SecurityFrameworkUtils.getLoginUserId(), id, signupId, reqVO.action());
        return success(true);
    }

    @PostMapping("/{id}/formation")
    @Operation(summary = "手动成团")
    @RequireAuthLevel(2)
    public CommonResult<Boolean> formation(@PathVariable("id") Long id) {
        demandService.manualFormation(SecurityFrameworkUtils.getLoginUserId(), id);
        return success(true);
    }

    @GetMapping("/{id}/signups")
    @Operation(summary = "报名列表")
    @RequireAuthLevel(2)
    public CommonResult<List<MateSignupDO>> signups(@PathVariable("id") Long id) {
        return success(demandService.listSignups(id));
    }
}
