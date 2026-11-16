package cn.iocoder.yudao.module.mate.service.risk;

import cn.iocoder.yudao.module.mate.dal.dataobject.risk.RiskEventDO;
import cn.iocoder.yudao.module.mate.dal.mysql.risk.RiskBlacklistMapper;
import cn.iocoder.yudao.module.mate.dal.mysql.risk.RiskEventMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * 风险评分判定表实现。规则按优先级短路，命中即返回，记录 risk_event。
 */
@Service
public class RiskServiceImpl implements RiskService {

    /** 大额阈值：超过则中风险延迟到账 */
    private static final BigDecimal LARGE_AMOUNT = new BigDecimal("200");

    @Resource
    private RiskBlacklistMapper blacklistMapper;
    @Resource
    private RiskEventMapper riskEventMapper;

    @Override
    public RiskResult evaluate(Long userId, Integer scene, BigDecimal amount) {
        int riskLevel;
        int advice;
        int ruleId;
        // 规则1（最高优先级）：命中黑名单 → 高风险冻结
        if (blacklistMapper.existsActiveByUser(userId)) {
            riskLevel = 2; advice = 2; ruleId = 1;
        }
        // 规则2：大额 → 中风险延迟
        else if (amount != null && amount.compareTo(LARGE_AMOUNT) > 0) {
            riskLevel = 1; advice = 1; ruleId = 2;
        }
        // 规则3：其余 → 低风险放行
        else {
            riskLevel = 0; advice = 0; ruleId = 3;
        }
        // 记录风险事件（只追加）
        riskEventMapper.insert(RiskEventDO.builder()
                .userId(userId).eventType("transfer").ruleId(ruleId).ruleVersion(1)
                .score(riskLevel * 50).advice(advice)
                .build());
        return new RiskResult(riskLevel, advice);
    }
}
