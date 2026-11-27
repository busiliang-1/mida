package cn.iocoder.yudao.module.mate.service.trade;

import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.module.mate.dal.dataobject.trade.WalletFlowDO;
import cn.iocoder.yudao.module.mate.dal.dataobject.trade.WalletTransferDO;
import cn.iocoder.yudao.module.mate.dal.mysql.trade.WalletFlowMapper;
import cn.iocoder.yudao.module.mate.dal.mysql.trade.WalletTransferMapper;
import cn.iocoder.yudao.module.mate.enums.trade.TransferStatusEnum;
import cn.iocoder.yudao.module.mate.framework.trade.TransferLimits;
import cn.iocoder.yudao.module.mate.framework.web.CursorPage;
import cn.iocoder.yudao.module.mate.service.auth.AuthService;
import cn.iocoder.yudao.module.mate.service.risk.RiskService;
import cn.iocoder.yudao.module.mate.service.risk.SensitiveWordService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.mate.enums.ErrorCodeConstants.AUTH_LEVEL_INSUFFICIENT;
import static cn.iocoder.yudao.module.mate.enums.ErrorCodeConstants.CONTENT_AUDIT_HIT;
import static cn.iocoder.yudao.module.mate.enums.ErrorCodeConstants.PARAM_INVALID;
import static cn.iocoder.yudao.module.mate.enums.ErrorCodeConstants.QUOTA_EXCEEDED;
import static cn.iocoder.yudao.module.mate.enums.ErrorCodeConstants.RESOURCE_OWNER_MISMATCH;
import static cn.iocoder.yudao.module.mate.enums.ErrorCodeConstants.RESOURCE_STATUS_INVALID;
import static cn.iocoder.yudao.module.mate.enums.ErrorCodeConstants.RISK_BLOCKED;

/**
 * M4 转账实现。
 * 资金一致性要点：
 * 1) 转账单先落库（唯一 transfer_no），再扣款，再决定延迟/入账；任一步异常整单回滚；
 * 2) 入账/撤销都用 CAS UPDATE（WHERE status = 1），定时任务与人工确认并发时只有一方成功；
 * 3) 幂等键不落独立表：transfer_no = "T" + sha256(userId:Idempotency-Key) 前 24 位，同键必然同号，靠唯一键去重。
 */
@Slf4j
@Service
@Validated
public class TransferServiceImpl implements TransferService {

    private static final int MAX_PAGE_SIZE = 50;
    private static final int TRANSFER_NO_KEY_LENGTH = 24;
    private static final BigDecimal MIN_AMOUNT = new BigDecimal("0.01");

    @Resource
    private WalletTransferMapper transferMapper;
    @Resource
    private WalletFlowMapper flowMapper;
    @Resource
    private WalletService walletService;
    @Resource
    private RiskService riskService;
    @Resource
    private AuthService authService;
    @Resource
    private SensitiveWordService sensitiveWordService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CreateResult create(Long payerId, CreateCmd cmd, String idempotencyKey) {
        validateCmd(payerId, cmd, idempotencyKey);
        // ① 等级：L1 一律 1003，且必须先于限额判断（TC-06）
        assertLevel(payerId);

        String transferNo = buildTransferNo(payerId, idempotencyKey);
        WalletTransferDO existing = transferMapper.selectByNo(transferNo);
        if (existing != null) {
            return toResult(existing);
        }
        // ② 限额
        assertQuota(payerId, cmd.amount());
        // ③ 风控
        RiskService.RiskResult risk = riskService.evaluate(payerId, cmd.scene(), cmd.amount());
        if (isHighRisk(risk)) {
            throw exception(RISK_BLOCKED);
        }
        if (StrUtil.isNotBlank(cmd.remark())
                && SensitiveWordService.AuditResult.HIT.equals(sensitiveWordService.match(cmd.remark()).verdict())) {
            throw exception(CONTENT_AUDIT_HIT);
        }

        boolean delayed = isDelayed(risk);
        BigDecimal amount = cmd.amount().setScale(2, RoundingMode.HALF_UP);
        WalletTransferDO transfer = WalletTransferDO.builder()
                .transferNo(transferNo)
                .payerId(payerId)
                .payeeId(cmd.payeeId())
                .sessionId(cmd.sessionId())
                .amount(amount)
                .scene(cmd.scene())
                .status(delayed ? TransferStatusEnum.PENDING_SETTLE.getStatus()
                        : TransferStatusEnum.WAITING_PAY.getStatus())
                .riskLevel(risk.riskLevel() == null ? 0 : risk.riskLevel())
                .delayUntil(delayed ? LocalDateTime.now().plusHours(TransferLimits.DELAY_HOURS) : null)
                .remark(cmd.remark())
                .build();
        try {
            transferMapper.insert(transfer);
        } catch (DuplicateKeyException ex) {
            // 并发同 Idempotency-Key：返回先落库的那一笔
            return toResult(transferMapper.selectByNo(transferNo));
        }

        // 扣款只走 debit（乐观锁），失败即 3001，整单回滚
        BigDecimal payerBalanceAfter = walletService.debit(payerId, amount);
        insertFlow(transfer.getTransferId(), payerId, 1, amount, payerBalanceAfter);

        if (delayed) {
            // ④ 中风险/新关系：钱已从付款方扣出，挂在待到账，等定时任务或收款确认入账
            return toResult(transfer);
        }
        // ⑤ 即时到账
        transferMapper.markSettledFromWaiting(transfer.getTransferId());
        BigDecimal payeeBalanceAfter = walletService.credit(cmd.payeeId(), amount);
        insertFlow(transfer.getTransferId(), cmd.payeeId(), 2, amount, payeeBalanceAfter);
        transfer.setStatus(TransferStatusEnum.SETTLED.getStatus());
        transfer.setFinishedAt(LocalDateTime.now());
        return toResult(transfer);
    }

    @Override
    public CursorPage<TransferItem> list(Long userId, Integer direction, String cursor, int size) {
        int pageSize = size <= 0 ? 20 : Math.min(size, MAX_PAGE_SIZE);
        Long beforeId = parseCursor(cursor);
        List<WalletTransferDO> rows = new ArrayList<>(
                transferMapper.selectMyTransfers(userId, direction, beforeId, pageSize + 1));
        boolean hasMore = rows.size() > pageSize;
        if (hasMore) {
            rows = new ArrayList<>(rows.subList(0, pageSize));
        }
        String nextCursor = hasMore ? String.valueOf(rows.get(rows.size() - 1).getTransferId()) : null;
        return new CursorPage<>(rows.stream().map(this::toItem).toList(), nextCursor, hasMore);
    }

    @Override
    public TransferDetail get(Long userId, String transferNo) {
        WalletTransferDO transfer = transferMapper.selectByNo(transferNo);
        if (transfer == null) {
            throw exception(RESOURCE_OWNER_MISMATCH);
        }
        boolean outgoing = Objects.equals(transfer.getPayerId(), userId);
        if (!outgoing && !Objects.equals(transfer.getPayeeId(), userId)) {
            throw exception(RESOURCE_OWNER_MISMATCH);
        }
        return new TransferDetail(toItem(transfer), outgoing);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Integer receipt(Long payeeId, String transferNo) {
        WalletTransferDO transfer = transferMapper.selectByNo(transferNo);
        if (transfer == null || !Objects.equals(transfer.getPayeeId(), payeeId)) {
            throw exception(RESOURCE_OWNER_MISMATCH);
        }
        if (Objects.equals(transfer.getStatus(), TransferStatusEnum.SETTLED.getStatus())) {
            return TransferStatusEnum.SETTLED.getStatus();
        }
        if (!Objects.equals(transfer.getStatus(), TransferStatusEnum.PENDING_SETTLE.getStatus())) {
            throw exception(RESOURCE_STATUS_INVALID);
        }
        settle(transfer);
        return TransferStatusEnum.SETTLED.getStatus();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Integer cancel(Long payerId, String transferNo) {
        WalletTransferDO transfer = transferMapper.selectByNo(transferNo);
        if (transfer == null || !Objects.equals(transfer.getPayerId(), payerId)) {
            throw exception(RESOURCE_OWNER_MISMATCH);
        }
        if (Objects.equals(transfer.getStatus(), TransferStatusEnum.CANCELLED.getStatus())) {
            return TransferStatusEnum.CANCELLED.getStatus();
        }
        if (!Objects.equals(transfer.getStatus(), TransferStatusEnum.PENDING_SETTLE.getStatus())) {
            throw exception(RESOURCE_STATUS_INVALID);
        }
        if (transferMapper.markCancelled(transfer.getTransferId()) == 0) {
            // 并发：定时任务已入账或已被撤销
            WalletTransferDO latest = transferMapper.selectByNo(transferNo);
            if (latest != null && Objects.equals(latest.getStatus(), TransferStatusEnum.CANCELLED.getStatus())) {
                return TransferStatusEnum.CANCELLED.getStatus();
            }
            throw exception(RESOURCE_STATUS_INVALID);
        }
        // 原路退回：付款方入账
        BigDecimal balanceAfter = walletService.credit(payerId, transfer.getAmount());
        insertFlow(transfer.getTransferId(), payerId, 2, transfer.getAmount(), balanceAfter);
        return TransferStatusEnum.CANCELLED.getStatus();
    }

    @Override
    public int settleDue() {
        List<WalletTransferDO> due = transferMapper.selectDue(LocalDateTime.now(), 100);
        int settled = 0;
        for (WalletTransferDO transfer : due) {
            try {
                if (settleOne(transfer.getTransferId())) {
                    settled++;
                }
            } catch (RuntimeException ex) {
                // 单笔失败不影响其他单；下一分钟会再次扫描到（CAS 保证不重复入账）
                log.error("[settleDue][transferId({}) 入账失败]", transfer.getTransferId(), ex);
            }
        }
        return settled;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean settleOne(Long transferId) {
        WalletTransferDO transfer = transferMapper.selectById(transferId);
        if (transfer == null || !Objects.equals(transfer.getStatus(), TransferStatusEnum.PENDING_SETTLE.getStatus())) {
            return false;
        }
        return settle(transfer);
    }

    // ========== 内部方法 ==========

    /** CAS 改状态成功者才入账，保证定时任务与收款确认不会双记 */
    private boolean settle(WalletTransferDO transfer) {
        if (transferMapper.markSettled(transfer.getTransferId()) == 0) {
            WalletTransferDO latest = transferMapper.selectById(transfer.getTransferId());
            return latest != null && Objects.equals(latest.getStatus(), TransferStatusEnum.SETTLED.getStatus());
        }
        BigDecimal balanceAfter = walletService.credit(transfer.getPayeeId(), transfer.getAmount());
        insertFlow(transfer.getTransferId(), transfer.getPayeeId(), 2, transfer.getAmount(), balanceAfter);
        return true;
    }

    private void validateCmd(Long payerId, CreateCmd cmd, String idempotencyKey) {
        if (cmd == null || cmd.payeeId() == null || cmd.scene() == null) {
            throw exception(PARAM_INVALID);
        }
        if (Objects.equals(payerId, cmd.payeeId())) {
            throw exception(PARAM_INVALID);
        }
        if (StrUtil.isBlank(idempotencyKey)) {
            throw exception(PARAM_INVALID);
        }
        if (cmd.amount() == null || cmd.amount().compareTo(MIN_AMOUNT) < 0) {
            throw exception(PARAM_INVALID);
        }
        // 金额最多两位小数，超过视为非法，避免四舍五入后与用户预期不一致
        if (cmd.amount().stripTrailingZeros().scale() > 2) {
            throw exception(PARAM_INVALID);
        }
        if (cmd.scene() < 1 || cmd.scene() > 3) {
            throw exception(PARAM_INVALID);
        }
    }

    /**
     * 等级闸门放在 Service 而非只靠 @RequireAuthLevel：
     * 分摊 #42 会在模块内直接调用转账，绕过 Controller 切面，资金动作必须在服务层再守一道。
     */
    private void assertLevel(Long payerId) {
        Integer level = authService.getAuthLevel(payerId);
        if (level == null || level < 2) {
            throw exception(AUTH_LEVEL_INSUFFICIENT);
        }
    }

    private void assertQuota(Long payerId, BigDecimal amount) {
        if (amount.compareTo(TransferLimits.SINGLE_LIMIT) > 0) {
            throw exception(QUOTA_EXCEEDED);
        }
        BigDecimal used = transferMapper.sumTodayOutgoing(payerId, LocalDate.now().atStartOfDay());
        BigDecimal total = (used == null ? BigDecimal.ZERO : used).add(amount);
        if (total.compareTo(TransferLimits.DAILY_LIMIT) > 0) {
            throw exception(QUOTA_EXCEEDED);
        }
    }

    private boolean isHighRisk(RiskService.RiskResult risk) {
        if (risk == null) {
            return false;
        }
        return Objects.equals(risk.riskLevel(), 2) || Objects.equals(risk.advice(), 2);
    }

    private boolean isDelayed(RiskService.RiskResult risk) {
        if (risk == null) {
            return false;
        }
        return Objects.equals(risk.riskLevel(), 1) || Objects.equals(risk.advice(), 1);
    }

    private void insertFlow(Long transferId, Long userId, Integer direction,
                            BigDecimal amount, BigDecimal balanceAfter) {
        flowMapper.insert(WalletFlowDO.builder()
                .transferId(transferId).userId(userId).direction(direction)
                .amount(amount).balanceAfter(balanceAfter).build());
    }

    private String buildTransferNo(Long payerId, String idempotencyKey) {
        String digest = DigestUtil.sha256Hex(payerId + ":" + idempotencyKey);
        return "T" + digest.substring(0, TRANSFER_NO_KEY_LENGTH).toUpperCase();
    }

    private Long parseCursor(String cursor) {
        if (StrUtil.isBlank(cursor)) {
            return null;
        }
        try {
            return Long.valueOf(cursor);
        } catch (NumberFormatException ex) {
            throw exception(PARAM_INVALID);
        }
    }

    private CreateResult toResult(WalletTransferDO transfer) {
        return new CreateResult(transfer.getTransferNo(), transfer.getStatus(), transfer.getDelayUntil());
    }

    private TransferItem toItem(WalletTransferDO transfer) {
        return new TransferItem(transfer.getTransferId(), transfer.getTransferNo(), transfer.getPayerId(),
                transfer.getPayeeId(), transfer.getAmount(), transfer.getScene(), transfer.getStatus(),
                transfer.getRiskLevel(), transfer.getDelayUntil(), transfer.getRemark(), transfer.getCreateTime());
    }
}
