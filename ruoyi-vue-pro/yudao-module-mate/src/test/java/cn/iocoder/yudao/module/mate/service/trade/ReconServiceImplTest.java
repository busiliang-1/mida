package cn.iocoder.yudao.module.mate.service.trade;

import cn.iocoder.yudao.module.mate.dal.dataobject.trade.MemberOrderDO;
import cn.iocoder.yudao.module.mate.dal.dataobject.trade.ReconDiffDO;
import cn.iocoder.yudao.module.mate.dal.dataobject.trade.TradePayNotifyDO;
import cn.iocoder.yudao.module.mate.dal.mysql.trade.MemberOrderMapper;
import cn.iocoder.yudao.module.mate.dal.mysql.trade.ReconDiffMapper;
import cn.iocoder.yudao.module.mate.dal.mysql.trade.TradePayNotifyMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * M4 对账：三类差异（金额/单边）都要能被识别，且重复跑不重复建单。
 */
@ExtendWith(MockitoExtension.class)
class ReconServiceImplTest {

    private static final LocalDateTime FROM = LocalDateTime.of(2026, 11, 26, 0, 0);
    private static final LocalDateTime TO = LocalDateTime.of(2026, 11, 27, 0, 0);

    @Mock
    private MemberOrderMapper memberOrderMapper;
    @Mock
    private TradePayNotifyMapper payNotifyMapper;
    @Mock
    private ReconDiffMapper reconDiffMapper;

    @InjectMocks
    private ReconServiceImpl reconService;

    private static MemberOrderDO order(String orderNo, String paidAmount) {
        return MemberOrderDO.builder().orderId(1L).orderNo(orderNo).userId(9L).orderType(1)
                .status(2).paidAmount(new BigDecimal(paidAmount)).build();
    }

    private static TradePayNotifyDO notify(String orderNo, String amount) {
        return TradePayNotifyDO.builder().eventId("e-" + orderNo).orderNo(orderNo)
                .tradeNo("T-1").amount(new BigDecimal(amount)).status("SUCCESS").build();
    }

    @Test
    void amountMismatchRecordedAsTypeAmount() {
        when(memberOrderMapper.selectPaidBetween(FROM, TO)).thenReturn(List.of(order("M-1", "9.90")));
        when(payNotifyMapper.selectSuccessBetween(FROM, TO)).thenReturn(List.of(notify("M-1", "0.01")));

        int created = reconService.reconcile(FROM, TO);

        assertEquals(1, created);
        ArgumentCaptor<ReconDiffDO> captor = ArgumentCaptor.forClass(ReconDiffDO.class);
        verify(reconDiffMapper).insert(captor.capture());
        assertEquals(1, captor.getValue().getType());
        assertEquals(new BigDecimal("9.90"), captor.getValue().getLocalAmount());
        assertEquals(new BigDecimal("0.01"), captor.getValue().getChannelAmount());
    }

    @Test
    void paidOrderWithoutNotifyIsSingleSide() {
        when(memberOrderMapper.selectPaidBetween(FROM, TO)).thenReturn(List.of(order("M-1", "9.90")));
        when(payNotifyMapper.selectSuccessBetween(FROM, TO)).thenReturn(List.of());

        assertEquals(1, reconService.reconcile(FROM, TO));
        ArgumentCaptor<ReconDiffDO> captor = ArgumentCaptor.forClass(ReconDiffDO.class);
        verify(reconDiffMapper).insert(captor.capture());
        assertEquals(3, captor.getValue().getType());
        assertNull(captor.getValue().getChannelAmount());
    }

    @Test
    void notifyWithoutEffectiveOrderIsSingleSide() {
        when(memberOrderMapper.selectPaidBetween(FROM, TO)).thenReturn(List.of());
        when(payNotifyMapper.selectSuccessBetween(FROM, TO)).thenReturn(List.of(notify("M-9", "6.00")));

        assertEquals(1, reconService.reconcile(FROM, TO));
        ArgumentCaptor<ReconDiffDO> captor = ArgumentCaptor.forClass(ReconDiffDO.class);
        verify(reconDiffMapper).insert(captor.capture());
        assertEquals(3, captor.getValue().getType());
        assertEquals("M-9", captor.getValue().getBizNo());
        assertNull(captor.getValue().getLocalAmount());
    }

    @Test
    void matchedPairProducesNoDiff() {
        when(memberOrderMapper.selectPaidBetween(FROM, TO)).thenReturn(List.of(order("M-1", "9.90")));
        when(payNotifyMapper.selectSuccessBetween(FROM, TO)).thenReturn(List.of(notify("M-1", "9.90")));

        assertEquals(0, reconService.reconcile(FROM, TO));
        verify(reconDiffMapper, never()).insert(any(ReconDiffDO.class));
    }

    @Test
    void repeatedRunDoesNotDuplicateDiff() {
        when(memberOrderMapper.selectPaidBetween(FROM, TO)).thenReturn(List.of(order("M-1", "9.90")));
        when(payNotifyMapper.selectSuccessBetween(FROM, TO)).thenReturn(List.of());
        when(reconDiffMapper.insert(any(ReconDiffDO.class))).thenThrow(new DuplicateKeyException("uk_diff_no"));

        // 同一天重跑：唯一键挡住，既不报错也不重复建单
        assertEquals(0, reconService.reconcile(FROM, TO));
    }
}
