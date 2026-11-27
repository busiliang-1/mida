package cn.iocoder.yudao.module.mate.service.trade;

import cn.iocoder.yudao.module.mate.dal.dataobject.trade.MemberBenefitDO;
import cn.iocoder.yudao.module.mate.dal.dataobject.trade.MemberOrderDO;
import cn.iocoder.yudao.module.mate.dal.mysql.trade.MemberBenefitMapper;
import cn.iocoder.yudao.module.mate.dal.mysql.trade.MemberOrderMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * M4 权益实现。
 * 关键点：expire_at 同时写进 member_order 和 member_benefit，续费基准只认订单表的最大未过期时间，
 * 权益表只负责"有哪些能力、值是多少"，避免两处口径打架。
 */
@Slf4j
@Service
@Validated
public class MemberBenefitServiceImpl implements MemberBenefitService {

    @Resource
    private MemberOrderMapper memberOrderMapper;
    @Resource
    private MemberBenefitMapper memberBenefitMapper;

    @Override
    public LocalDateTime nextExpireAt(Long userId, int months) {
        LocalDateTime now = LocalDateTime.now();
        MemberOrderDO active = memberOrderMapper.selectLatestActive(userId, now);
        // 未过期则从原到期时间叠加，避免用户续费"吃掉"剩余天数
        LocalDateTime base = active != null && active.getExpireAt() != null && active.getExpireAt().isAfter(now)
                ? active.getExpireAt() : now;
        return base.plusMonths(months);
    }

    @Override
    public void issue(Long userId, String sourceOrderNo, Map<String, String> benefits, LocalDateTime expireAt) {
        if (benefits == null || benefits.isEmpty()) {
            return;
        }
        for (Map.Entry<String, String> entry : benefits.entrySet()) {
            MemberBenefitDO benefit = MemberBenefitDO.builder()
                    .userId(userId)
                    .benefitKey(entry.getKey())
                    .benefitValue(entry.getValue())
                    .sourceOrderNo(sourceOrderNo)
                    .expireAt(expireAt)
                    .status(0)
                    .build();
            try {
                memberBenefitMapper.insert(benefit);
            } catch (DuplicateKeyException ignored) {
                // 同一订单重复发放：唯一键已保证只有一行，按幂等跳过
                log.info("[issue][orderNo({}) key({}) 已发放，跳过]", sourceOrderNo, entry.getKey());
            }
        }
    }

    @Override
    public String getBenefitValue(Long userId, String benefitKey, String defaultValue) {
        MemberBenefitDO benefit = memberBenefitMapper.selectActive(userId, benefitKey, LocalDateTime.now());
        return benefit == null ? defaultValue : benefit.getBenefitValue();
    }
}
