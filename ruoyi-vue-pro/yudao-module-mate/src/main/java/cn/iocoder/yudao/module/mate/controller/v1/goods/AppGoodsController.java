package cn.iocoder.yudao.module.mate.controller.v1.goods;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.mate.dal.dataobject.auth.SysUserDO;
import cn.iocoder.yudao.module.mate.framework.auth.RequireAuthLevel;
import cn.iocoder.yudao.module.mate.framework.web.CursorPage;
import cn.iocoder.yudao.module.mate.framework.web.CursorResult;
import cn.iocoder.yudao.module.mate.service.auth.AuthService;
import cn.iocoder.yudao.module.mate.service.goods.GoodsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
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

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

/**
 * M7 二手接口 #65 / #66 / #67 / #68。
 */
@Tag(name = "用户端 - 二手交易")
@RestController
@RequestMapping("/api/v1/goods")
@Validated
public class AppGoodsController {

    @Resource
    private GoodsService goodsService;
    @Resource
    private AuthService authService;

    public record PublishReqVO(@NotBlank @Size(max = 60) String title,
                               @NotBlank @Size(max = 16) String category,
                               @NotNull @DecimalMin("0.01") BigDecimal price,
                               @Size(max = 1000) String description,
                               @NotEmpty List<String> images) {
    }

    public record PublishRespVO(Long goodsId, Integer status) {
    }

    public record GoodsVO(Long goodsId, Long sellerId, String title, String category, BigDecimal price,
                          String description, List<String> images, Integer status, LocalDateTime createTime) {
    }

    public record SellerVO(Long userId, String nickname, Integer authLevel, Integer schoolId) {
    }

    public record DetailRespVO(GoodsVO goods, SellerVO seller) {
    }

    public record OffRespVO(Integer status) {
    }

    public record CreateOrderRespVO(String orderNo, Integer status) {
    }

    @PostMapping
    @Operation(summary = "挂售二手（需 L3）")
    @RequireAuthLevel(3)
    public CommonResult<PublishRespVO> publish(@RequestHeader("Idempotency-Key") String idempotencyKey,
                                               @Valid @RequestBody PublishReqVO reqVO) {
        Long goodsId = goodsService.publish(getLoginUserId(), new GoodsService.PublishCmd(
                reqVO.title(), reqVO.category(), reqVO.price(), reqVO.description(), reqVO.images()));
        return success(new PublishRespVO(goodsId, 0));
    }

    @GetMapping
    @Operation(summary = "二手列表（分类筛选 + 游标）")
    @RequireAuthLevel(1)
    public CommonResult<CursorResult<GoodsVO>> list(@RequestParam(required = false) String category,
                                                    @RequestParam(required = false) String cursor,
                                                    @RequestParam(required = false, defaultValue = "20") Integer size) {
        CursorPage<GoodsService.GoodsItem> page = goodsService.list(category, cursor, size);
        List<GoodsVO> list = page.list().stream().map(AppGoodsController::toVO).toList();
        return success(new CursorResult<>(list, page.nextCursor(), page.hasMore()));
    }

    @GetMapping("/{id}")
    @Operation(summary = "二手详情 + 卖家名片")
    @RequireAuthLevel(1)
    public CommonResult<DetailRespVO> get(@PathVariable("id") Long id) {
        GoodsService.GoodsItem goods = goodsService.get(id);
        SysUserDO seller = authService.getUser(goods.sellerId());
        SellerVO sellerVO = seller == null ? null : new SellerVO(seller.getUserId(), seller.getNickname(),
                seller.getAuthLevel(), seller.getSchoolId());
        return success(new DetailRespVO(toVO(goods), sellerVO));
    }

    @PatchMapping("/{id}/off")
    @Operation(summary = "下架（仅卖家，需 L3）")
    @RequireAuthLevel(3)
    public CommonResult<OffRespVO> offShelf(@RequestHeader("Idempotency-Key") String idempotencyKey,
                                            @PathVariable("id") Long id) {
        return success(new OffRespVO(goodsService.offShelf(getLoginUserId(), id)));
    }

    @PostMapping("/{id}/orders")
    @Operation(summary = "二手下单（需 L2，乐观锁防重复）")
    @RequireAuthLevel(2)
    public CommonResult<CreateOrderRespVO> createOrder(@RequestHeader("Idempotency-Key") String idempotencyKey,
                                                       @PathVariable("id") Long goodsId) {
        GoodsService.OrderResult result = goodsService.createOrder(getLoginUserId(), goodsId, idempotencyKey);
        return success(new CreateOrderRespVO(result.orderNo(), result.status()));
    }

    private static GoodsVO toVO(GoodsService.GoodsItem item) {
        return new GoodsVO(item.goodsId(), item.sellerId(), item.title(), item.category(), item.price(),
                item.description(), item.images(), item.status(), item.createTime());
    }
}
