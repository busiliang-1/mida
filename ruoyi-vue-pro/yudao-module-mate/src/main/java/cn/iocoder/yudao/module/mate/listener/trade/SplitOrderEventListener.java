package cn.iocoder.yudao.module.mate.listener.trade;

import cn.iocoder.yudao.module.mate.event.DemandFormedEvent;
import cn.iocoder.yudao.module.mate.service.trade.SplitOrderService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 成团 → 生成分摊草稿（status=0），等活动结束后由发起人填总额。
 * 与 M3 的建群监听器并行消费同一个事件，互不依赖。
 */
@Slf4j
@Component
public class SplitOrderEventListener {

    @Resource
    private SplitOrderService splitOrderService;

    @EventListener
    @Transactional(rollbackFor = Exception.class)
    public void onDemandFormed(DemandFormedEvent event) {
        try {
            Long splitId = splitOrderService.createDraft(event.demandId(), event.publisherId(), event.needCount());
            log.info("[onDemandFormed][demandId({}) splitId({}) 分摊草稿就绪]", event.demandId(), splitId);
        } catch (RuntimeException ex) {
            log.error("[onDemandFormed][demandId({}) 生成分摊草稿失败]", event.demandId(), ex);
        }
    }
}
