package cn.iocoder.yudao.module.mate.service.trade;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.module.mate.dal.dataobject.trade.MemberOrderDO;
import cn.iocoder.yudao.module.mate.dal.dataobject.trade.ReconDiffDO;
import cn.iocoder.yudao.module.mate.dal.dataobject.trade.TradePayNotifyDO;
import cn.iocoder.yudao.module.mate.dal.mysql.trade.MemberOrderMapper;
import cn.iocoder.yudao.module.mate.dal.mysql.trade.ReconDiffMapper;
import cn.iocoder.yudao.module.mate.dal.mysql.trade.TradePayNotifyMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * M4 对账实现。
 *
 * <p>两侧账本：本地侧 = member_order 里已生效（status=2）的订单；渠道侧 = trade_pay_notify 里
 * status=SUCCESS 的回调。Mock 支付没有真实渠道方，所以"渠道侧"就是回调记录本身——
 * 这不是偷懒，而是能覆盖真实场景里最要命的三类差异：金额不符、钱到单未生效、单生效无凭据。
 *
 * <p>不写成一条 FULL OUTER JOIN 的 SQL：MySQL 没有 full join，两次 LEFT JOIN + UNION 可读性差、
 * 单测还得起真库。一天几十笔的量，内存比对足够，且能纯 Mock 测。
 */
@Slf4j
@Service
@Validated
public class ReconServiceImpl implements ReconService {

    /** 对账差错类型：1 金额 2 状态 3 单边 */
    private static final int TYPE_AMOUNT = 1;
    private static final int TYPE_SINGLE_SIDE = 3;
    private static final int STATUS_PENDING = 0;
    private static final int DIFF_NO_KEY_LENGTH = 24;

    @Resource
    private MemberOrderMapper memberOrderMapper;
    @Resource
    private TradePayNotifyMapper payNotifyMapper;
    @Resource
    private ReconDiffMapper reconDiffMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int reconcile(LocalDateTime from, LocalDateTime to) {
        List<MemberOrderDO> orders = memberOrderMapper.selectPaidBetween(from, to);
        List<TradePayNotifyDO> notifies = payNotifyMapper.selectSuccessBetween(from, to);

        Map<String, List<TradePayNotifyDO>> byOrderNo = notifies.stream()
                .collect(Collectors.groupingBy(TradePayNotifyDO::getOrderNo,
                        LinkedHashMap::new, Collectors.toList()));
        String day = from.toLocalDate().toString();
        int created = 0;

        // 1) 本地有单：必须能对上渠道侧，且金额一致
        for (MemberOrderDO order : orders) {
            List<TradePayNotifyDO> matched = byOrderNo.remove(order.getOrderNo());
            BigDecimal localAmount = order.getPaidAmount() == null ? BigDecimal.ZERO : order.getPaidAmount();
            if (matched == null || matched.isEmpty()) {
                // 单生效了却没有成功回调：要么回调丢了，要么订单被非支付路径改成生效
                created += record(day, order.getOrderNo(), localAmount, null, TYPE_SINGLE_SIDE);
                continue;
            }
            BigDecimal channelAmount = sum(matched);
            if (channelAmount.compareTo(localAmount) != 0) {
                created += record(day, order.getOrderNo(), localAmount, channelAmount, TYPE_AMOUNT);
            }
        }

        // 2) 剩下的回调：钱到了，但本地没有生效的单
        for (Map.Entry<String, List<TradePayNotifyDO>> entry : byOrderNo.entrySet()) {
            created += record(day, entry.getKey(), null, sum(entry.getValue()), TYPE_SINGLE_SIDE);
        }
        if (created > 0) {
            log.warn("[reconcile][{} ~ {} 发现 {} 笔差异]", from, to, created);
        }
        return created;
    }

    private int record(String day, String bizNo, BigDecimal localAmount,
                       BigDecimal channelAmount, int type) {
        ReconDiffDO diff = ReconDiffDO.builder()
                .diffNo("R" + digest(day, bizNo, type))
                .bizNo(bizNo)
                .localAmount(localAmount)
                .channelAmount(channelAmount)
                .type(type)
                .status(STATUS_PENDING)
                .build();
        try {
            reconDiffMapper.insert(diff);
            return 1;
        } catch (DuplicateKeyException ex) {
            // 同一窗口重复跑（人工补跑/任务重试）：uk_diff_no 挡住，不重复建单
            return 0;
        }
    }

    private String digest(String day, String bizNo, int type) {
        return DigestUtil.sha256Hex(day + ":" + bizNo + ":" + type)
                .substring(0, DIFF_NO_KEY_LENGTH).toUpperCase();
    }

    private BigDecimal sum(List<TradePayNotifyDO> list) {
        return list.stream()
                .map(n -> n.getAmount() == null ? BigDecimal.ZERO : n.getAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
