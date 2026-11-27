package cn.iocoder.yudao.module.mate.service.trade;

import java.math.BigDecimal;
import java.util.List;

/**
 * M4 活动分摊 Service。
 * 流程：成团建草稿(0) → 发起人填总额(1 待确认) → 参与人逐个确认(2 收集中) → 全员确认拆 N 笔 scene=2 转账(3 已完成)；
 * 任何一人拒绝 → 4 已取消，整单退回重算。
 */
public interface SplitOrderService {

    Long createDraft(Long demandId, Long initiatorId, Integer memberCount);

    Detail detail(Long userId, Long splitId);

    BigDecimal fillTotal(Long userId, Long splitId, BigDecimal totalAmount);

    Integer confirm(Long userId, Long splitId, boolean confirm);

    record Detail(Long splitId, String splitNo, Long demandId, Long initiatorId, BigDecimal totalAmount,
                  Integer memberCount, BigDecimal perAmount, Integer status,
                  List<Long> memberIds, List<Long> confirmedUserIds) {
    }
}
