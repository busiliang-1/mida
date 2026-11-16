package cn.iocoder.yudao.module.mate.service.risk;

/**
 * M6 风险评分 Service。供 M4 转账准入同步调用。
 */
public interface RiskService {

    /** 风险结果：riskLevel 0低1中2高；advice 0放行1延迟2冻结3转人工 */
    record RiskResult(Integer riskLevel, Integer advice) {}

    /**
     * 评估风险（判定表，优先级短路）。
     * 一期简化：黑名单→高/冻结；大额→中/延迟；其余→低/放行。
     * 关系时长维度待 M3 接入后补充。
     */
    RiskResult evaluate(Long userId, Integer scene, java.math.BigDecimal amount);
}
