package cn.iocoder.yudao.module.mate.job.trade;

import cn.iocoder.yudao.module.mate.service.trade.ReconService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * M4 日终对账：每天 02:30 对前一日做一次，差异落 recon_diff，由 #48 内部接口查询/处置。
 *
 * <p>对账失败不影响其他任务，异常在这里吞掉并记录——重跑只需再调一次
 * {@link ReconService#reconcile}（diff_no 幂等，不会重复建单）。
 */
@Slf4j
@Component
public class ReconJob {

    @Resource
    private ReconService reconService;

    @Scheduled(cron = "0 30 2 * * ?")
    public void dailyReconcile() {
        LocalDateTime to = LocalDate.now().atStartOfDay();
        LocalDateTime from = to.minusDays(1);
        try {
            int created = reconService.reconcile(from, to);
            log.info("[dailyReconcile][{} ~ {} 对账完成，新增差异 {} 笔]", from, to, created);
        } catch (Exception ex) {
            log.error("[dailyReconcile][{} ~ {} 对账失败，可人工重跑]", from, to, ex);
        }
    }
}
