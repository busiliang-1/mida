package cn.iocoder.yudao.module.mate.framework.websocket;

import cn.iocoder.yudao.framework.common.biz.system.oauth2.OAuth2TokenCommonApi;
import jakarta.annotation.Resource;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * M3 STOMP 配置。
 * 端点 /ws；订阅 /user/queue/messages、/user/queue/card-update；心跳由 SockJS/浏览器自带。
 * 不用 @EnableWebSocket（raw handler），避免和底座 /infra/ws 的 JSON 通道互相覆盖。
 */
@Configuration
@EnableWebSocketMessageBroker
public class ImWebSocketConfiguration implements WebSocketMessageBrokerConfigurer {

    @Resource
    private OAuth2TokenCommonApi oauth2TokenApi;

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")
                .addInterceptors(new ImHandshakeInterceptor(oauth2TokenApi))
                .setHandshakeHandler(new ImPrincipalHandshakeHandler());
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // 点对点队列 + 主题；生产环境换 RabbitMQ/ActiveMQ 只需换 registry
        registry.enableSimpleBroker("/queue", "/topic");
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ImStompAuthChannelInterceptor(oauth2TokenApi));
    }
}
