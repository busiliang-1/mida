package cn.iocoder.yudao.module.mate.framework.websocket;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.biz.system.oauth2.OAuth2TokenCommonApi;
import cn.iocoder.yudao.framework.common.biz.system.oauth2.dto.OAuth2AccessTokenCheckRespDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;

/**
 * STOMP 入站拦截器：
 * 1) CONNECT 时若握手没带 Principal，则从 STOMP 原生头 Authorization/token 再解析一次并 setUser；
 * 2) 解析不到用户的 CONNECT、以及没有 Principal 的 SEND/SUBSCRIBE 一律拒绝（返回 null 即断开）。
 */
@RequiredArgsConstructor
public class ImStompAuthChannelInterceptor implements ChannelInterceptor {

    private final OAuth2TokenCommonApi oauth2TokenApi;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }
        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            if (accessor.getUser() == null) {
                Long userId = resolveUserId(accessor);
                if (userId == null) {
                    return null; // 拒绝连接
                }
                accessor.setUser(new ImPrincipal(String.valueOf(userId)));
            }
            return message;
        }
        if ((StompCommand.SEND.equals(accessor.getCommand())
                || StompCommand.SUBSCRIBE.equals(accessor.getCommand()))
                && accessor.getUser() == null) {
            return null;
        }
        return message;
    }

    private Long resolveUserId(StompHeaderAccessor accessor) {
        String token = firstHeader(accessor, "Authorization");
        if (StrUtil.isNotBlank(token) && token.startsWith("Bearer ")) {
            token = token.substring(7);
        }
        if (StrUtil.isBlank(token)) {
            token = firstHeader(accessor, "token");
        }
        if (StrUtil.isBlank(token)) {
            return null;
        }
        try {
            OAuth2AccessTokenCheckRespDTO accessToken = oauth2TokenApi.checkAccessToken(token);
            return accessToken == null ? null : accessToken.getUserId();
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private String firstHeader(StompHeaderAccessor accessor, String name) {
        return accessor.getFirstNativeHeader(name);
    }
}
