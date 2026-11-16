package cn.iocoder.yudao.module.mate.event;

/**
 * 需求单成团事件。M3 订阅建群、M4 订阅生成分摊草稿。
 * 一期经 Spring ApplicationEventPublisher 进程内发布；后续可换 outbox/MQ。
 */
public record DemandFormedEvent(Long demandId, Long publisherId, Integer needCount, Integer feeMode) {
}
