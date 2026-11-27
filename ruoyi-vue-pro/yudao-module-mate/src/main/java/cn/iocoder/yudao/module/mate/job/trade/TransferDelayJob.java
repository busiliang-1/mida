package cn.iocoder.yudao.module.mate.job.trade;

import cn.iocoder.yudao.module.mate.service.trade.TransferService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * M4 延迟到账扫描：每分钟把 status=1 且 delay_until<=now 的单子入账。
 * @EnableScheduling 由 yudao-spring-boot-starter-job 提供，无需重复声明。
 * 入账幂等由 wallet_transfer 的 CAS（WHERE status=1）保证：多实例/重复扫描不会重复加钱。
 */
@Slf4j
@Component
public class TransferDelayJob {

    @Resource
    private TransferService transferService;

    @Scheduled(cron = "0 * * * * ?")
    public void settleDueTransfers() {
        int settled = transferService.settleDue();
        if (settled > 0) {
            log.info("[settleDueTransfers][本次入账 {} 笔]", settled);
        }
    }
}
