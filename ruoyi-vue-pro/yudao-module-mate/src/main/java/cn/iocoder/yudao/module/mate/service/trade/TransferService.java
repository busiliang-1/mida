package cn.iocoder.yudao.module.mate.service.trade;

import cn.iocoder.yudao.module.mate.framework.web.CursorPage;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * M4 转账 Service。
 * 准入顺序固定：①认证等级（不足 1003，且必须早于限额，满足 TC-06）②限额（3002）
 * ③风控（高风险 3003）④中风险/新关系写 delay_until 待到账 ⑤其余即时到账。
 */
public interface TransferService {

    CreateResult create(Long payerId, CreateCmd cmd, String idempotencyKey);

    CursorPage<TransferItem> list(Long userId, Integer direction, String cursor, int size);

    TransferDetail get(Long userId, String transferNo);

    /** 收款确认：待到账 → 已到账，重复确认幂等返回 2 */
    Integer receipt(Long payeeId, String transferNo);

    /** 撤销：仅待到账可撤销，重复撤销幂等返回 3 */
    Integer cancel(Long payerId, String transferNo);

    /** 定时任务入口：扫描到期单入账 */
    int settleDue();

    /** 单笔入账（CAS 防重），供定时任务与收款确认复用 */
    boolean settleOne(Long transferId);

    record CreateCmd(Long payeeId, Long sessionId, BigDecimal amount, Integer scene, String remark) {
    }

    record CreateResult(String transferNo, Integer status, LocalDateTime delayUntil) {
    }

    record TransferItem(Long transferId, String transferNo, Long payerId, Long payeeId, BigDecimal amount,
                        Integer scene, Integer status, Integer riskLevel, LocalDateTime delayUntil,
                        String remark, LocalDateTime createTime) {
    }

    record TransferDetail(TransferItem transfer, boolean outgoing) {
    }
}
