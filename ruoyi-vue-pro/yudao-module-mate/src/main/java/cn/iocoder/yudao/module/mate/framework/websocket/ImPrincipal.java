package cn.iocoder.yudao.module.mate.framework.websocket;

import java.security.Principal;

/**
 * STOMP 会话身份：name 就是平台 userId。
 * 用 user destination（/user/queue/messages）推送时，Spring 按 Principal.getName() 路由。
 */
public record ImPrincipal(String name) implements Principal {

    @Override
    public String getName() {
        return name;
    }
}
