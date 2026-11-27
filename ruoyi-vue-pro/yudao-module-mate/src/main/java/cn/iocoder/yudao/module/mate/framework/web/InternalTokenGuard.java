package cn.iocoder.yudao.module.mate.framework.web;

import cn.hutool.core.util.StrUtil;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.mate.enums.ErrorCodeConstants.UNAUTHORIZED;

/**
 * 内部接口共享密钥校验（#32 IM 校验 / #48 对账 / #58 审核队列）。
 *
 * <p>配置来源 {@code yudao.mate.internal-token}，默认空。<b>空配置 = 全部拒绝</b>——
 * 不能因为漏配就把内部接口变成无鉴权入口，这是这条校验唯一有意义的行为。
 * 部署时通过环境变量注入，不写进代码或仓库里的 yaml。
 */
@Slf4j
@Component
public class InternalTokenGuard {

    @Value("${yudao.mate.internal-token:}")
    private String internalToken;

    @PostConstruct
    public void warnIfAbsent() {
        if (StrUtil.isBlank(internalToken)) {
            log.warn("[InternalTokenGuard][yudao.mate.internal-token 未配置，所有内部接口将拒绝访问]");
        }
    }

    /**
     * 校验请求头里的共享密钥，不通过抛 1002。
     */
    public void check(String token) {
        if (StrUtil.isBlank(internalToken) || !internalToken.equals(token)) {
            throw exception(UNAUTHORIZED);
        }
    }
}
