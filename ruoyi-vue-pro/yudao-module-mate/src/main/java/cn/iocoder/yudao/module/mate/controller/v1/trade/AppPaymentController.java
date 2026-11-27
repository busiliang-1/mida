package cn.iocoder.yudao.module.mate.controller.v1.trade;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.mate.framework.auth.RequireAuthLevel;
import cn.iocoder.yudao.module.mate.service.trade.MemberOrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.annotation.security.PermitAll;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

/**
 * M4 支付回调 #46（验签，无用户令牌）与订单状态 #47。
 */
@Tag(name = "支付回调 / 订单状态")
@RestController
@Validated
public class AppPaymentController {

    @Resource
    private MemberOrderService memberOrderService;

    public record CallbackReqVO(@NotBlank String event_id, @NotBlank String order_no,
                                @NotBlank String trade_no, @NotNull BigDecimal amount,
                                @NotBlank String status, @NotNull LocalDateTime occurred_at) {
    }

    public record CallbackRespVO(int code) {
    }

    public record OrderStatusRespVO(Integer status) {
    }

    @PostMapping("/api/v1/payment-callbacks")
    @Operation(summary = "支付回调（MockPayChannel HMAC 验签，event_id 去重）")
    @PermitAll
    public ResponseEntity<CommonResult<CallbackRespVO>> callback(
            @RequestHeader(value = "Signature", required = false) String signature,
            @Valid @RequestBody CallbackReqVO reqVO) {
        try {
            memberOrderService.handleCallback(signature,
                    new MemberOrderService.CallbackCmd(reqVO.event_id(), reqVO.order_no(), reqVO.trade_no(),
                            reqVO.amount(), reqVO.status(), reqVO.occurred_at()),
                    reqVO.toString());
            return ResponseEntity.ok(CommonResult.success(new CallbackRespVO(0)));
        } catch (RuntimeException ex) {
            // 验签失败返回 401，让机构按失败处理，不吞成 200
            if (ex instanceof cn.iocoder.yudao.framework.common.exception.ServiceException se
                    && se.getCode() != null && se.getCode() == 5001) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(CommonResult.error(5001, "支付回调验签失败"));
            }
            throw ex;
        }
    }

    @GetMapping("/api/v1/orders/{no}")
    @Operation(summary = "订单状态")
    @RequireAuthLevel(1)
    public CommonResult<OrderStatusRespVO> getOrderStatus(@PathVariable("no") String no) {
        return success(new OrderStatusRespVO(memberOrderService.getOrderStatus(getLoginUserId(), no)));
    }
}
