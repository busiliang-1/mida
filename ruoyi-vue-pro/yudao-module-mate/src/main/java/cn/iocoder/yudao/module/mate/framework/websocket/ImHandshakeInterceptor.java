package cn.iocoder.yudao.module.mate.framework.websocket;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.biz.system.oauth2.OAuth2TokenCommonApi;
import cn.iocoder.yudao.framework.common.biz.system.oauth2.dto.OAuth2AccessTokenCheckRespDTO;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;

/**
 * STOMP 握手拦截器：从 token 解析 userId 放入 session attributes。
 * token 来源优先级：SecurityContext（HTTP 过滤器已认证）→ Authorization 头 → ?token= 查询参数
 * （浏览器原生 WebSocket 不能自定义 Header，所以必须支持 query 参数）。
 */
@RequiredArgsConstructor
public class ImHandshakeInterceptor implements HandshakeInterceptor {

    public static final String ATTR_USER_ID = "IM_USER_ID";

    private final OAuth2TokenCommonApi oauth2TokenApi;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        Long userId = resolveUserId(request);
        if (userId == null) {
            return false; // 拒绝握手，避免匿名连接占用会话
        }
        attributes.put(ATTR_USER_ID, userId);
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        // do nothing
    }

    private Long resolveUserId(ServerHttpRequest request) {
        LoginUser loginUser = SecurityFrameworkUtils.getLoginUser();
        if (loginUser != null && loginUser.getId() != null) {
            return loginUser.getId();
        }
        String token = request.getHeaders().getFirst("Authorization");
        if (StrUtil.isNotBlank(token) && token.startsWith("Bearer ")) {
            token = token.substring(7);
        }
        if (StrUtil.isBlank(token)) {
            token = UriComponentsBuilder.fromUri(request.getURI()).build()
                    .getQueryParams().getFirst("token");
        }
        if (StrUtil.isBlank(token)) {
            return null;
        }
        try {
            OAuth2AccessTokenCheckRespDTO accessToken = oauth2TokenApi.checkAccessToken(token);
            return accessToken == null ? null : accessToken.getUserId();
        } catch (RuntimeException ex) {
            // token 失效/过期：拒绝握手即可，不把异常抛给容器
            return null;
        }
    }
}
