package cn.iocoder.yudao.module.mate.event;

/**
 * 双向匹配建立事件。M3 订阅开通单聊会话。
 */
public record MatchCreatedEvent(Long userLowId, Long userHighId, Integer source) {
}
