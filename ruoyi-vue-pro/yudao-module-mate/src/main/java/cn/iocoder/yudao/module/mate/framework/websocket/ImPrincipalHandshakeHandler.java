package cn.iocoder.yudao.module.mate.framework.websocket;

import org.springframework.http.server.ServerHttpRequest;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;

import java.security.Principal;
import java.util.Map;

/**
 * 把握手阶段解析出的 userId 变成 STOMP 会话的 Principal。
 * 这是"订阅 /user/queue/messages 只能收到自己的消息"的根：Principal 不可由客户端伪造。
 */
public class ImPrincipalHandshakeHandler extends DefaultHandshakeHandler {

    @Override
    protected Principal determineUser(ServerHttpRequest request, WebSocketHandler wsHandler,
                                      Map<String, Object> attributes) {
        Object userId = attributes.get(ImHandshakeInterceptor.ATTR_USER_ID);
        if (userId == null) {
            return null;
        }
        return new ImPrincipal(String.valueOf(userId));
    }
}
