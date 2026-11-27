package cn.iocoder.yudao.module.mate.service.im;

import jakarta.annotation.Resource;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

/**
 * M3 在线直推。
 * 客户端订阅：/user/queue/messages（新消息/撤回）、/user/queue/card-update（卡片状态变更）。
 * 用户维度由 STOMP Principal.name = userId 决定，Principal 在握手阶段由 token 解析而来。
 */
@Service
public class ImPushService {

    public static final String DEST_MESSAGES = "/queue/messages";
    public static final String DEST_CARD_UPDATE = "/queue/card-update";

    @Resource
    private SimpMessagingTemplate messagingTemplate;

    public void pushMessage(Long userId, Object payload) {
        messagingTemplate.convertAndSendToUser(String.valueOf(userId), DEST_MESSAGES, payload);
    }

    public void pushCardUpdate(Long userId, Object payload) {
        messagingTemplate.convertAndSendToUser(String.valueOf(userId), DEST_CARD_UPDATE, payload);
    }
}
