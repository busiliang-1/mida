package cn.iocoder.yudao.module.mate.controller.v1.trade;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.mate.framework.auth.RequireAuthLevel;
import cn.iocoder.yudao.module.mate.service.trade.WalletService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

/**
 * M4 钱包接口 #39：余额 / 冻结 / 当日额度余量。
 */
@Tag(name = "用户端 - 钱包")
@RestController
@RequestMapping("/api/v1/wallet")
@Validated
public class AppWalletController {

    @Resource
    private WalletService walletService;

    public record WalletRespVO(BigDecimal available, BigDecimal frozen, BigDecimal remainQuota) {
    }

    @GetMapping
    @Operation(summary = "我的钱包")
    @RequireAuthLevel(2)
    public CommonResult<WalletRespVO> getWallet() {
        WalletService.WalletView view = walletService.getWallet(getLoginUserId());
        return success(new WalletRespVO(view.available(), view.frozen(), view.remainQuota()));
    }
}
