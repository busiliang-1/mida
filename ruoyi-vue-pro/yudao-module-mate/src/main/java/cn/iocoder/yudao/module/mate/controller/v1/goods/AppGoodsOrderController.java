package cn.iocoder.yudao.module.mate.controller.v1.goods;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.mate.framework.auth.RequireAuthLevel;
import cn.iocoder.yudao.module.mate.framework.web.CursorPage;
import cn.iocoder.yudao.module.mate.framework.web.CursorResult;
import cn.iocoder.yudao.module.mate.service.goods.GoodsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

/**
 * M7 二手订单接口 #69 / #70 / #71。
 */
@Tag(name = "用户端 - 二手订单")
@RestController
@RequestMapping("/api/v1/goods-orders")
@Validated
public class AppGoodsOrderController {

    @Resource
    private GoodsService goodsService;

    public record ConfirmRespVO(Integer status) {
    }

    public record OrderVO(Long orderId, String orderNo, Long goodsId, Long buyerId, Long sellerId,
                          BigDecimal amount, Integer status, LocalDateTime createTime) {
    }

    @PatchMapping("/{id}/confirm")
    @Operation(summary = "线下确认完成（发 goods.sold，无资金托管）")
    @RequireAuthLevel(2)
    public CommonResult<ConfirmRespVO> confirm(@RequestHeader("Idempotency-Key") String idempotencyKey,
                                              @PathVariable("id") Long orderId) {
        return success(new ConfirmRespVO(goodsService.confirm(getLoginUserId(), orderId)));
    }

    @GetMapping
    @Operation(summary = "我的二手订单（买/卖）")
    @RequireAuthLevel(2)
    public CommonResult<CursorResult<OrderVO>> myOrders(@RequestParam(required = false) Integer role,
                                                        @RequestParam(required = false) String cursor,
                                                        @RequestParam(required = false, defaultValue = "20") Integer size) {
        CursorPage<GoodsService.OrderItem> page = goodsService.myOrders(getLoginUserId(), role, cursor, size);
        List<OrderVO> list = page.list().stream()
                .map(o -> new OrderVO(o.orderId(), o.orderNo(), o.goodsId(), o.buyerId(), o.sellerId(),
                        o.amount(), o.status(), o.createTime()))
                .toList();
        return success(new CursorResult<>(list, page.nextCursor(), page.hasMore()));
    }
}
