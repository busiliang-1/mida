package cn.iocoder.yudao.module.mate.service.trade;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * M4 权益 Service：续费时间计算 + 键值化发放。
 * 续费规则：未过期 = 原到期时间 + 时长；已过期/无会员 = now() + 时长。
 */
public interface MemberBenefitService {

    /** 计算本次订购后的权益到期时间 */
    LocalDateTime nextExpireAt(Long userId, int months);

    /** 发放权益；同一订单重复发放由 uk(user_id, benefit_key, source_order_no) 兜住，不抛错 */
    void issue(Long userId, String sourceOrderNo, Map<String, String> benefits, LocalDateTime expireAt);

    /** 读权益值，没有则返回默认值（业务侧不写死常量） */
    String getBenefitValue(Long userId, String benefitKey, String defaultValue);
}
