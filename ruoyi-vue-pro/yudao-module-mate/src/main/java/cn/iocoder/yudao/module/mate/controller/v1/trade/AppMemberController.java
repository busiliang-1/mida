package cn.iocoder.yudao.module.mate.controller.v1.trade;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.mate.framework.auth.RequireAuthLevel;
import cn.iocoder.yudao.module.mate.service.trade.MemberOrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

/**
 * M4 会员接口 #43 / #44 / #45。
 */
@Tag(name = "用户端 - 会员")
@RestController
@Validated
public class AppMemberController {

    @Resource
    private MemberOrderService memberOrderService;

    public record PackageVO(Integer packageId, String name, Integer months, BigDecimal price,
                            Map<String, String> benefits) {
    }

    public record CreateMemberOrderReqVO(@NotNull Integer packageId, @NotNull Integer durationMonth) {
    }

    public record ValueAddedReqVO(@NotNull Integer type, String targetId) {
    }

    public record OrderRespVO(String orderNo, BigDecimal amount, Integer status, String payUrl) {
    }

    @GetMapping("/api/v1/member-packages")
    @Operation(summary = "会员套餐与权益列表")
    @RequireAuthLevel(1)
    public CommonResult<List<PackageVO>> listPackages() {
        return success(memberOrderService.listPackages().stream()
                .map(p -> new PackageVO(p.packageId(), p.name(), p.months(), p.price(), p.benefits()))
                .toList());
    }

    @PostMapping("/api/v1/member-orders")
    @Operation(summary = "创建会员订单（返回收银台链接）")
    @RequireAuthLevel(1)
    public CommonResult<OrderRespVO> createMemberOrder(@RequestHeader("Idempotency-Key") String idempotencyKey,
                                                       @Valid @RequestBody CreateMemberOrderReqVO reqVO) {
        MemberOrderService.CreateOrderResult result = memberOrderService.createMemberOrder(
                getLoginUserId(), reqVO.packageId(), reqVO.durationMonth(), idempotencyKey);
        return success(new OrderRespVO(result.orderNo(), result.amount(), result.status(), result.payUrl()));
    }

    @PostMapping("/api/v1/value-added-orders")
    @Operation(summary = "增值服务订单（置顶/曝光）")
    @RequireAuthLevel(2)
    public CommonResult<OrderRespVO> createValueAddedOrder(@RequestHeader("Idempotency-Key") String idempotencyKey,
                                                           @Valid @RequestBody ValueAddedReqVO reqVO) {
        MemberOrderService.CreateOrderResult result = memberOrderService.createValueAddedOrder(
                getLoginUserId(), reqVO.type(), reqVO.targetId(), idempotencyKey);
        return success(new OrderRespVO(result.orderNo(), result.amount(), result.status(), result.payUrl()));
    }
}
