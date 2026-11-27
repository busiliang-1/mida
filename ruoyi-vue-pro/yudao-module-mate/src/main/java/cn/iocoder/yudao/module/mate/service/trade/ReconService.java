package cn.iocoder.yudao.module.mate.service.trade;

import java.time.LocalDateTime;

/**
 * M4 对账：本地订单账本 vs 支付回调账本，差异落 recon_diff（#48 可查、可处置）。
 */
public interface ReconService {

    /**
     * 对账一个时间窗。同一窗口重复执行不会重复建差错单（diff_no 幂等）。
     *
     * @param from 起始时间（含）
     * @param to   结束时间（不含）
     * @return 本次新增的差异条数
     */
    int reconcile(LocalDateTime from, LocalDateTime to);
}
