package cn.iocoder.yudao.module.mate.controller.v1.trade;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.mate.framework.auth.RequireAuthLevel;
import cn.iocoder.yudao.module.mate.framework.web.CursorPage;
import cn.iocoder.yudao.module.mate.framework.web.CursorResult;
import cn.iocoder.yudao.module.mate.service.trade.TransferService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;
import static cn.iocoder.yudao.module.mate.enums.ErrorCodeConstants.UNAUTHORIZED;

/**
 * M4 转账接口 #34 / #35 / #36 / #37 / #38。
 * 写接口一律要求 Idempotency-Key（项目铁律 3），重复请求返回首次结果。
 */
@Tag(name = "用户端 - 转账")
@RestController
@RequestMapping("/api/v1/transfers")
@Validated
public class AppTransferController {

    @Resource
    private TransferService transferService;

    public record CreateReqVO(@NotNull Long payeeId, Long sessionId,
                              @NotNull @DecimalMin("0.01") BigDecimal amount,
                              @NotNull Integer scene,
                              @Size(max = 100) String remark) {
    }

    public record CreateRespVO(String transferNo, Integer status, LocalDateTime delayUntil) {
    }

    public record TransferVO(Long transferId, String transferNo, Long payerId, Long payeeId, BigDecimal amount,
                             Integer scene, Integer status, Integer riskLevel, LocalDateTime delayUntil,
                             String remark, LocalDateTime createTime) {
    }

    public record DetailRespVO(TransferVO transfer, boolean outgoing) {
    }

    public record StatusRespVO(Integer status) {
    }

    @PostMapping
    @Operation(summary = "发起转账（准入判定 + 风控）")
    @RequireAuthLevel(2)
    public CommonResult<CreateRespVO> create(@RequestHeader("Idempotency-Key") String idempotencyKey,
                                             @Valid @RequestBody CreateReqVO reqVO) {
        TransferService.CreateResult result = transferService.create(requireLogin(),
                new TransferService.CreateCmd(reqVO.payeeId(), reqVO.sessionId(), reqVO.amount(),
                        reqVO.scene(), reqVO.remark()), idempotencyKey);
        return success(new CreateRespVO(result.transferNo(), result.status(), result.delayUntil()));
    }

    @GetMapping
    @Operation(summary = "转账列表（游标）")
    @RequireAuthLevel(2)
    public CommonResult<CursorResult<TransferVO>> list(@RequestParam(required = false) Integer direction,
                                                       @RequestParam(required = false) String cursor,
                                                       @RequestParam(required = false, defaultValue = "20") Integer size) {
        CursorPage<TransferService.TransferItem> page = transferService.list(requireLogin(), direction, cursor, size);
        List<TransferVO> list = page.list().stream().map(AppTransferController::toVO).toList();
        return success(new CursorResult<>(list, page.nextCursor(), page.hasMore()));
    }

    @GetMapping("/{no}")
    @Operation(summary = "转账详情（卡片渲染源）")
    @RequireAuthLevel(2)
    public CommonResult<DetailRespVO> get(@PathVariable("no") String no) {
        TransferService.TransferDetail detail = transferService.get(requireLogin(), no);
        return success(new DetailRespVO(toVO(detail.transfer()), detail.outgoing()));
    }

    @PostMapping("/{no}/receipt")
    @Operation(summary = "收款确认")
    @RequireAuthLevel(2)
    public CommonResult<StatusRespVO> receipt(@RequestHeader("Idempotency-Key") String idempotencyKey,
                                              @PathVariable("no") String no) {
        return success(new StatusRespVO(transferService.receipt(requireLogin(), no)));
    }

    @PostMapping("/{no}/cancellation")
    @Operation(summary = "撤销（仅待到账）")
    @RequireAuthLevel(2)
    public CommonResult<StatusRespVO> cancel(@RequestHeader("Idempotency-Key") String idempotencyKey,
                                             @PathVariable("no") String no) {
        return success(new StatusRespVO(transferService.cancel(requireLogin(), no)));
    }

    private Long requireLogin() {
        Long userId = getLoginUserId();
        if (userId == null) {
            throw exception(UNAUTHORIZED);
        }
        return userId;
    }

    private static TransferVO toVO(TransferService.TransferItem item) {
        return new TransferVO(item.transferId(), item.transferNo(), item.payerId(), item.payeeId(),
                item.amount(), item.scene(), item.status(), item.riskLevel(), item.delayUntil(),
                item.remark(), item.createTime());
    }
}
