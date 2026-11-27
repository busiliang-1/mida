package cn.iocoder.yudao.module.mate.service.trade;

import cn.iocoder.yudao.module.mate.dal.dataobject.trade.TradeSplitOrderDO;
import cn.iocoder.yudao.module.mate.dal.mysql.trade.TradeSplitOrderMapper;
import cn.iocoder.yudao.module.mate.framework.trade.SplitAmountCalculator;
import cn.iocoder.yudao.module.mate.service.match.DemandService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * M4 分摊：人均两位小数、最后一人补差；一人拒绝整单取消。
 */
@ExtendWith(MockitoExtension.class)
class SplitOrderServiceImplTest {

    private static final Long SPLIT_ID = 3001L;
    private static final Long INITIATOR = 41L;
    private static final Long MEMBER = 42L;

    @Mock
    private TradeSplitOrderMapper splitOrderMapper;
    @Mock
    private DemandService demandService;
    @Mock
    private TransferService transferService;
    @Mock
    private StringRedisTemplate stringRedisTemplate;
    @Mock
    private SetOperations<String, String> setOperations;

    @InjectMocks
    private SplitOrderServiceImpl splitOrderService;

    private TradeSplitOrderDO split(int status) {
        return TradeSplitOrderDO.builder().splitId(SPLIT_ID).splitNo("S-1").demandId(9001L)
                .initiatorId(INITIATOR).totalAmount(new BigDecimal("100.00")).memberCount(3)
                .perAmount(new BigDecimal("50.00")).status(status).build();
    }

    private static cn.iocoder.yudao.module.mate.dal.dataobject.match.MateSignupDO signup(Long userId) {
        return cn.iocoder.yudao.module.mate.dal.dataobject.match.MateSignupDO.builder()
                .signupId(userId).demandId(9001L).userId(userId).auditStatus(1).build();
    }

    @Test
    void allocateKeepsSumAndLastPersonBearsRounding() {
        List<BigDecimal> amounts = SplitAmountCalculator.allocate(new BigDecimal("100.00"), 3);

        assertEquals(new BigDecimal("33.33"), amounts.get(0));
        assertEquals(new BigDecimal("33.33"), amounts.get(1));
        assertEquals(new BigDecimal("33.34"), amounts.get(2));
        assertEquals(new BigDecimal("100.00"), amounts.stream().reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    @Test
    void oneRejectCancelsWholeSplit() {
        when(splitOrderMapper.selectById(SPLIT_ID)).thenReturn(split(1));
        when(demandService.listSignups(9001L)).thenReturn(List.of(signup(MEMBER)));
        when(splitOrderMapper.updateById(any(TradeSplitOrderDO.class))).thenReturn(1);

        assertEquals(4, splitOrderService.confirm(MEMBER, SPLIT_ID, false));
        verify(stringRedisTemplate).delete(anyString());
        verify(transferService, org.mockito.Mockito.never())
                .create(org.mockito.ArgumentMatchers.anyLong(), any(), anyString());
    }

    @Test
    void allConfirmedSplitsIntoTransfers() {
        when(splitOrderMapper.selectById(SPLIT_ID)).thenReturn(split(1));
        when(demandService.listSignups(9001L)).thenReturn(List.of(signup(MEMBER)));
        when(stringRedisTemplate.opsForSet()).thenReturn(setOperations);
        when(setOperations.add(anyString(), anyString())).thenReturn(1L);
        when(stringRedisTemplate.expire(anyString(), any(java.time.Duration.class))).thenReturn(true);
        // 只有 MEMBER 一个付款人（发起人不付给自己），确认数达到要求
        when(setOperations.members(anyString())).thenReturn(Set.of(String.valueOf(MEMBER)));
        when(splitOrderMapper.updateById(any(TradeSplitOrderDO.class))).thenReturn(1);

        assertEquals(3, splitOrderService.confirm(MEMBER, SPLIT_ID, true));
        verify(transferService).create(eq(MEMBER), any(TransferService.CreateCmd.class), anyString());
    }

    @Test
    void partialConfirmationStaysCollecting() {
        when(splitOrderMapper.selectById(SPLIT_ID)).thenReturn(split(1));
        when(demandService.listSignups(9001L)).thenReturn(List.of(signup(MEMBER), signup(43L)));
        when(stringRedisTemplate.opsForSet()).thenReturn(setOperations);
        when(setOperations.add(anyString(), anyString())).thenReturn(1L);
        when(stringRedisTemplate.expire(anyString(), any(java.time.Duration.class))).thenReturn(true);
        when(setOperations.members(anyString())).thenReturn(Set.of(String.valueOf(MEMBER)));
        when(splitOrderMapper.updateById(any(TradeSplitOrderDO.class))).thenReturn(1);

        assertEquals(2, splitOrderService.confirm(MEMBER, SPLIT_ID, true));
        verify(transferService, org.mockito.Mockito.never())
                .create(org.mockito.ArgumentMatchers.anyLong(), any(), anyString());
    }
}
