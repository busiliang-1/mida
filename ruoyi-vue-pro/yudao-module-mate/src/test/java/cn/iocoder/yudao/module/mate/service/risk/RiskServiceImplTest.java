package cn.iocoder.yudao.module.mate.service.risk;

import cn.iocoder.yudao.module.mate.dal.mysql.risk.RiskBlacklistMapper;
import cn.iocoder.yudao.module.mate.dal.mysql.risk.RiskEventMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

/**
 * 风险评分判定表测试（优先级短路）：黑名单→高/冻结；大额→中/延迟；其余→低/放行。
 */
@ExtendWith(MockitoExtension.class)
class RiskServiceImplTest {

    @Mock
    private RiskBlacklistMapper blacklistMapper;
    @Mock
    private RiskEventMapper riskEventMapper;
    @InjectMocks
    private RiskServiceImpl riskService;

    @Test
    void blacklistHighFreeze() {
        when(blacklistMapper.existsActiveByUser(anyLong())).thenReturn(true);
        RiskService.RiskResult r = riskService.evaluate(1L, 1, new BigDecimal("10"));
        assertEquals(2, r.riskLevel());
        assertEquals(2, r.advice());
    }

    @Test
    void largeAmountMediumDelay() {
        when(blacklistMapper.existsActiveByUser(anyLong())).thenReturn(false);
        RiskService.RiskResult r = riskService.evaluate(1L, 1, new BigDecimal("300"));
        assertEquals(1, r.riskLevel());
        assertEquals(1, r.advice());
    }

    @Test
    void normalLowPass() {
        when(blacklistMapper.existsActiveByUser(anyLong())).thenReturn(false);
        RiskService.RiskResult r = riskService.evaluate(1L, 1, new BigDecimal("50"));
        assertEquals(0, r.riskLevel());
        assertEquals(0, r.advice());
    }

    @Test
    void boundaryExactly200IsLow() {
        when(blacklistMapper.existsActiveByUser(anyLong())).thenReturn(false);
        RiskService.RiskResult r = riskService.evaluate(1L, 1, new BigDecimal("200"));
        assertEquals(0, r.riskLevel()); // 恰好等于阈值不算大额
    }
}
