package cn.iocoder.yudao.module.mate.controller.v1.trade;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.mate.framework.auth.RequireAuthLevel;
import cn.iocoder.yudao.module.mate.service.trade.SplitOrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

/**
 * M4 分摊接口 #40 / #41 / #42。
 */
@Tag(name = "用户端 - 活动分摊")
@RestController
@RequestMapping("/api/v1/splits")
@Validated
public class AppSplitController {

    @Resource
    private SplitOrderService splitOrderService;

    public record DetailRespVO(Long splitId, String splitNo, Long demandId, Long initiatorId,
                               BigDecimal totalAmount, Integer memberCount, BigDecimal perAmount, Integer status,
                               List<Long> memberIds, List<Long> confirmedUserIds) {
    }

    public record FillTotalReqVO(@NotNull @DecimalMin("0.01") BigDecimal totalAmount) {
    }

    public record PerAmountRespVO(BigDecimal perAmount) {
    }

    public record ConfirmReqVO(@NotNull Boolean confirm) {
    }

    public record ConfirmRespVO(Integer splitStatus) {
    }

    @GetMapping("/{id}")
    @Operation(summary = "分摊单详情 + 各人确认状态")
    @RequireAuthLevel(2)
    public CommonResult<DetailRespVO> detail(@PathVariable("id") Long id) {
        SplitOrderService.Detail detail = splitOrderService.detail(getLoginUserId(), id);
        return success(new DetailRespVO(detail.splitId(), detail.splitNo(), detail.demandId(), detail.initiatorId(),
                detail.totalAmount(), detail.memberCount(), detail.perAmount(), detail.status(),
                detail.memberIds(), detail.confirmedUserIds()));
    }

    @PostMapping("/{id}/total")
    @Operation(summary = "填分摊总额（发起人）")
    @RequireAuthLevel(2)
    public CommonResult<PerAmountRespVO> fillTotal(@RequestHeader("Idempotency-Key") String idempotencyKey,
                                                   @PathVariable("id") Long id,
                                                   @Valid @RequestBody FillTotalReqVO reqVO) {
        return success(new PerAmountRespVO(splitOrderService.fillTotal(getLoginUserId(), id, reqVO.totalAmount())));
    }

    @PostMapping("/{id}/confirmations")
    @Operation(summary = "分摊确认/拒绝（全员确认后拆 N 笔转账）")
    @RequireAuthLevel(2)
    public CommonResult<ConfirmRespVO> confirm(@RequestHeader("Idempotency-Key") String idempotencyKey,
                                               @PathVariable("id") Long id,
                                               @Valid @RequestBody ConfirmReqVO reqVO) {
        return success(new ConfirmRespVO(splitOrderService.confirm(getLoginUserId(), id, reqVO.confirm())));
    }
}
