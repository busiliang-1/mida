package cn.iocoder.yudao.module.mate.framework.trade;

import cn.hutool.core.util.HexUtil;
import cn.iocoder.yudao.module.mate.enums.ErrorCodeConstants;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;

/**
 * M4 支付通道替身：提供收银台链接 + HMAC 验签。
 * 真实机构回调通常是「按字段字典序拼串 + HMAC-SHA256」，这里用固定顺序的规范串模拟，
 * 换成真通道时只需替换 {@link #canonical} 与验签实现，业务代码不动。
 */
@Component
public class MockPayChannel {

    public static final String CHANNEL = "MOCK";

    @Value("${yudao.mate.pay.mock-secret:mock-pay-secret}")
    private String secret;

    /** 收银台链接（前端跳转用，非真实支付） */
    public String checkoutUrl(String orderNo, BigDecimal amount) {
        return "https://mock-pay.local/cashier?orderNo=" + orderNo + "&amount=" + amount.toPlainString()
                + "&channel=" + CHANNEL;
    }

    /** 规范串：eventId|orderNo|tradeNo|amount|status */
    public static String canonical(String eventId, String orderNo, String tradeNo, BigDecimal amount, String status) {
        return String.join("|", eventId, orderNo, tradeNo,
                amount == null ? "" : amount.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString(),
                status);
    }

    public String sign(String canonical) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexUtil.encodeHexStr(mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("HMAC 计算失败", ex);
        }
    }

    /** 验签失败直接 5001，且不写任何业务数据 */
    public void verify(String canonical, String signature) {
        if (signature == null || !sign(canonical).equalsIgnoreCase(signature)) {
            throw exception(ErrorCodeConstants.PAY_SIGN_INVALID);
        }
    }
}
