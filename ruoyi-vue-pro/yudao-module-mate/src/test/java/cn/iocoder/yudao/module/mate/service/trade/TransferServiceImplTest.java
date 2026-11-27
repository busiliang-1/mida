package cn.iocoder.yudao.module.mate.service.trade;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.mate.dal.dataobject.trade.WalletTransferDO;
import cn.iocoder.yudao.module.mate.dal.mysql.trade.WalletFlowMapper;
import cn.iocoder.yudao.module.mate.dal.mysql.trade.WalletTransferMapper;
import cn.iocoder.yudao.module.mate.service.auth.AuthService;
import cn.iocoder.yudao.module.mate.service.risk.RiskService;
import cn.iocoder.yudao.module.mate.service.risk.SensitiveWordService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * M4 转账准入判定表（TC-01 ~ TC-10），Mock 掉 M6 的 RiskService。
 *
 * TC-01 L2 小额低风险 → 即时到账(2)
 * TC-02 L2 中风险 → 待到账(1) + delay_until
 * TC-03 高风险 → 3003
 * TC-04 余额不足 → 3001
 * TC-05 单笔超限 → 3002
 * TC-06 L1 + 超限 → 1003（等级优先，必须早于限额）
 * TC-07 转给自己 → 1001
 * TC-08 同 Idempotency-Key 重放 → 返回首次单号，不重复扣款
 * TC-09 延迟期撤销 → 3，原路退回
 * TC-10 延迟到期入账 → 2，仅入账一次
 */
@ExtendWith(MockitoExtension.class)
class TransferServiceImplTest {

    private static final Long PAYER = 1L;
    private static final Long PAYEE = 2L;

    @Mock
    private WalletTransferMapper transferMapper;
    @Mock
    private WalletFlowMapper flowMapper;
    @Mock
    private WalletService walletService;
    @Mock
    private RiskService riskService;
    @Mock
    private AuthService authService;
    @Mock
    private SensitiveWordService sensitiveWordService;

    @InjectMocks
    private TransferServiceImpl transferService;

    @ParameterizedTest(name = "{0}")
    @CsvSource({
            "TC-01 低风险即时到账, 2, 100.00, 0, 0, false, false, 0, 2",
            "TC-02 中风险延迟到账, 2, 300.00, 1, 1, false, false, 0, 1",
            "TC-03 高风险拦截, 2, 100.00, 2, 2, false, false, 3003, -1",
            "TC-04 余额不足, 2, 100.00, 0, 0, false, true, 3001, -1",
            "TC-05 单笔超限, 2, 2500.00, 0, 0, false, false, 3002, -1",
            "TC-06 L1超限先判等级, 1, 2500.00, 0, 0, false, false, 1003, -1",
            "TC-07 转给自己, 2, 100.00, 0, 0, true, false, 1001, -1"
    })
    void admission(String tc, int level, String amount, int riskLevel, int advice,
                   boolean selfTransfer, boolean debitFail, int expectCode, int expectStatus) {
        Long payeeId = selfTransfer ? PAYER : PAYEE;
        lenient().when(authService.getAuthLevel(PAYER)).thenReturn(level);
        lenient().when(transferMapper.selectByNo(anyString())).thenReturn(null);
        lenient().when(transferMapper.sumTodayOutgoing(eq(PAYER), any())).thenReturn(new BigDecimal("0.00"));
        lenient().when(riskService.evaluate(anyLong(), anyInt(), any()))
                .thenReturn(new RiskService.RiskResult(riskLevel, advice));
        lenient().when(sensitiveWordService.match(anyString()))
                .thenReturn(new SensitiveWordService.AuditResult(SensitiveWordService.AuditResult.PASS, List.of(), 1));
        lenient().when(walletService.debit(eq(PAYER), any())).thenAnswer(invocation -> {
            if (debitFail) {
                throw new ServiceException(3001, "余额不足");
            }
            return new BigDecimal("900.00");
        });
        lenient().when(walletService.credit(eq(PAYEE), any())).thenReturn(new BigDecimal("400.00"));
        lenient().when(transferMapper.insert(any(WalletTransferDO.class))).thenAnswer(invocation -> {
            WalletTransferDO transfer = invocation.getArgument(0);
            transfer.setTransferId(500L);
            return 1;
        });
        lenient().when(transferMapper.markSettledFromWaiting(anyLong())).thenReturn(1);

        TransferService.CreateCmd cmd = new TransferService.CreateCmd(payeeId, 9001L,
                new BigDecimal(amount), 1, "午饭");
        if (expectCode != 0) {
            ServiceException ex = assertThrows(ServiceException.class,
                    () -> transferService.create(PAYER, cmd, "key-" + tc));
            assertEquals(expectCode, ex.getCode());
            return;
        }
        TransferService.CreateResult result = transferService.create(PAYER, cmd, "key-" + tc);
        assertEquals(expectStatus, result.status());
        if (expectStatus == 1) {
            // 延迟到账必须写 delay_until，定时任务才有扫描依据
            assertEquals(true, result.delayUntil() != null && result.delayUntil().isAfter(LocalDateTime.now()));
        }
    }

    @Test
    void tc08_sameIdempotencyKeyReturnsFirstTransfer() {
        WalletTransferDO existing = WalletTransferDO.builder().transferId(900L).transferNo("T-EXIST")
                .payerId(PAYER).payeeId(PAYEE).amount(new BigDecimal("100.00")).scene(1)
                .status(2).riskLevel(0).build();
        when(authService.getAuthLevel(PAYER)).thenReturn(2);
        when(transferMapper.selectByNo(anyString())).thenReturn(existing);

        TransferService.CreateResult result = transferService.create(PAYER,
                new TransferService.CreateCmd(PAYEE, null, new BigDecimal("100.00"), 1, null), "same-key");

        assertEquals("T-EXIST", result.transferNo());
        assertEquals(2, result.status());
    }

    @Test
    void tc09_cancelPendingTransferRefundsPayer() {
        WalletTransferDO pending = WalletTransferDO.builder().transferId(901L).transferNo("T-1")
                .payerId(PAYER).payeeId(PAYEE).amount(new BigDecimal("300.00")).scene(1).status(1).build();
        when(transferMapper.selectByNo("T-1")).thenReturn(pending);
        when(transferMapper.markCancelled(901L)).thenReturn(1);
        when(walletService.credit(eq(PAYER), any())).thenReturn(new BigDecimal("600.00"));

        assertEquals(3, transferService.cancel(PAYER, "T-1"));
    }

    @Test
    void tc09_cannotCancelSettledTransfer() {
        WalletTransferDO settled = WalletTransferDO.builder().transferId(902L).transferNo("T-2")
                .payerId(PAYER).payeeId(PAYEE).amount(new BigDecimal("300.00")).scene(1).status(2).build();
        when(transferMapper.selectByNo("T-2")).thenReturn(settled);

        ServiceException ex = assertThrows(ServiceException.class, () -> transferService.cancel(PAYER, "T-2"));

        assertEquals(2002, ex.getCode());
    }

    @Test
    void tc10_settleDueCreditsPayeeOnce() {
        WalletTransferDO pending = WalletTransferDO.builder().transferId(903L).transferNo("T-3")
                .payerId(PAYER).payeeId(PAYEE).amount(new BigDecimal("300.00")).scene(1).status(1)
                .delayUntil(LocalDateTime.now().minusMinutes(1)).build();
        when(transferMapper.selectDue(any(), anyInt())).thenReturn(List.of(pending));
        when(transferMapper.selectById(903L)).thenReturn(pending);
        when(transferMapper.markSettled(903L)).thenReturn(1);
        when(walletService.credit(eq(PAYEE), any())).thenReturn(new BigDecimal("700.00"));

        assertEquals(1, transferService.settleDue());
    }

    @Test
    void tc10_receiptIsIdempotent() {
        WalletTransferDO settled = WalletTransferDO.builder().transferId(904L).transferNo("T-4")
                .payerId(PAYER).payeeId(PAYEE).amount(new BigDecimal("300.00")).scene(1).status(2).build();
        when(transferMapper.selectByNo("T-4")).thenReturn(settled);

        assertEquals(2, transferService.receipt(PAYEE, "T-4"));
    }
}
