package cn.iocoder.yudao.module.mate.service.trade;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.mate.dal.dataobject.trade.MemberOrderDO;
import cn.iocoder.yudao.module.mate.dal.dataobject.trade.TradePayNotifyDO;
import cn.iocoder.yudao.module.mate.dal.mysql.trade.MemberOrderMapper;
import cn.iocoder.yudao.module.mate.dal.mysql.trade.TradePayNotifyMapper;
import cn.iocoder.yudao.module.mate.event.MemberOrderPaidEvent;
import cn.iocoder.yudao.module.mate.framework.trade.MockPayChannel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * M4 支付回调幂等：同一 event_id 重复回调 10 次，只发一次权益、只改一次单。
 */
@ExtendWith(MockitoExtension.class)
class MemberOrderServiceImplTest {

    @Mock
    private MemberOrderMapper memberOrderMapper;
    @Mock
    private TradePayNotifyMapper payNotifyMapper;
    @Mock
    private MemberBenefitService memberBenefitService;
    @Mock
    private MockPayChannel payChannel;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private MemberOrderServiceImpl memberOrderService;

    private MemberOrderDO pendingOrder() {
        return MemberOrderDO.builder().orderId(1L).orderNo("M-1").userId(9L).orderType(1)
                .packageId(1).durationMonth(1).amount(new BigDecimal("9.90")).status(0).build();
    }

    @Test
    void repeatedCallbackIssuesBenefitOnlyOnce() {
        when(memberOrderMapper.selectByNo("M-1")).thenReturn(pendingOrder());
        when(memberBenefitService.nextExpireAt(anyLong(), anyInt()))
                .thenReturn(LocalDateTime.now().plusMonths(1));
        when(memberOrderMapper.updateById(any(MemberOrderDO.class))).thenReturn(1);

        AtomicInteger lookups = new AtomicInteger();
        when(payNotifyMapper.selectByEventId("e-1")).thenAnswer(invocation ->
                lookups.getAndIncrement() == 0 ? null : TradePayNotifyDO.builder().id(1L).eventId("e-1").build());

        MemberOrderService.CallbackCmd cmd = new MemberOrderService.CallbackCmd("e-1", "M-1", "T-1",
                new BigDecimal("9.90"), "SUCCESS", LocalDateTime.now());
        for (int i = 0; i < 10; i++) {
            memberOrderService.handleCallback("sign", cmd, "raw");
        }

        verify(payNotifyMapper, times(1)).insert(any(TradePayNotifyDO.class));
        verify(memberOrderMapper, times(1)).updateById(any(MemberOrderDO.class));
        verify(memberBenefitService, times(1)).issue(anyLong(), anyString(), anyMap(), any());
        // order.paid 同样只发一次：重复回调不能把事件放大成 10 条
        verify(eventPublisher, times(1)).publishEvent(any(MemberOrderPaidEvent.class));
    }

    @Test
    void amountMismatchRejected() {
        when(memberOrderMapper.selectByNo("M-1")).thenReturn(pendingOrder());

        MemberOrderService.CallbackCmd cmd = new MemberOrderService.CallbackCmd("e-2", "M-1", "T-2",
                new BigDecimal("0.01"), "SUCCESS", LocalDateTime.now());

        ServiceException ex = assertThrows(ServiceException.class,
                () -> memberOrderService.handleCallback("sign", cmd, "raw"));
        assertEquals(5001, ex.getCode());
    }

    @Test
    void signVerifiedBeforeAnyWrite() {
        org.mockito.Mockito.doThrow(new ServiceException(5001, "支付回调验签失败"))
                .when(payChannel).verify(anyString(), any());

        MemberOrderService.CallbackCmd cmd = new MemberOrderService.CallbackCmd("e-3", "M-1", "T-3",
                new BigDecimal("9.90"), "SUCCESS", LocalDateTime.now());

        ServiceException ex = assertThrows(ServiceException.class,
                () -> memberOrderService.handleCallback("bad", cmd, "raw"));
        assertEquals(5001, ex.getCode());
        verify(payNotifyMapper, times(0)).insert(any(TradePayNotifyDO.class));
    }

    @Test
    void packageBenefitsAreKeyValuePairs() {
        assertEquals(Map.of("publish_daily_limit", "5", "feed_weight", "1"),
                cn.iocoder.yudao.module.mate.enums.trade.MemberPackageEnum.BASIC.getBenefits());
    }
}
