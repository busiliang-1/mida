package cn.iocoder.yudao.module.mate.service.trade;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.module.mate.dal.dataobject.trade.TradeSplitOrderDO;
import cn.iocoder.yudao.module.mate.dal.mysql.trade.TradeSplitOrderMapper;
import cn.iocoder.yudao.module.mate.framework.trade.SplitAmountCalculator;
import cn.iocoder.yudao.module.mate.service.match.DemandService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.mate.enums.ErrorCodeConstants.PARAM_INVALID;
import static cn.iocoder.yudao.module.mate.enums.ErrorCodeConstants.RESOURCE_OWNER_MISMATCH;
import static cn.iocoder.yudao.module.mate.enums.ErrorCodeConstants.RESOURCE_STATUS_INVALID;

/**
 * M4 分摊实现。
 * 为什么确认状态放 Redis：23 张契约表里没有"分摊确认记录表"，不臆造表结构；
 * 确认只是短期协同状态，订单状态(0~4)落 MySQL 作为权威状态，Redis 只存"谁点了确认"，7 天过期。
 */
@Slf4j
@Service
@Validated
public class SplitOrderServiceImpl implements SplitOrderService {

    private static final String CONFIRM_KEY_PREFIX = "mate:split:confirm:";

    @Resource
    private TradeSplitOrderMapper splitOrderMapper;
    @Resource
    private DemandService demandService;
    @Resource
    private TransferService transferService;
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Long createDraft(Long demandId, Long initiatorId, Integer memberCount) {
        TradeSplitOrderDO existing = splitOrderMapper.selectByDemandId(demandId);
        if (existing != null) {
            return existing.getSplitId();
        }
        TradeSplitOrderDO draft = TradeSplitOrderDO.builder()
                .splitNo("S" + DigestUtil.sha256Hex(String.valueOf(demandId)).substring(0, 24).toUpperCase())
                .demandId(demandId)
                .initiatorId(initiatorId)
                .memberCount(memberCount == null || memberCount <= 0 ? 2 : memberCount)
                .status(0)
                .build();
        try {
            splitOrderMapper.insert(draft);
            return draft.getSplitId();
        } catch (DuplicateKeyException ex) {
            return splitOrderMapper.selectByDemandId(demandId).getSplitId();
        }
    }

    @Override
    public Detail detail(Long userId, Long splitId) {
        TradeSplitOrderDO split = requireSplit(splitId);
        List<Long> members = loadMembers(split);
        if (!members.contains(userId)) {
            throw exception(RESOURCE_OWNER_MISMATCH);
        }
        Set<String> confirmed = stringRedisTemplate.opsForSet().members(confirmKey(splitId));
        List<Long> confirmedIds = confirmed == null ? List.of()
                : confirmed.stream().map(Long::valueOf).toList();
        return new Detail(split.getSplitId(), split.getSplitNo(), split.getDemandId(), split.getInitiatorId(),
                split.getTotalAmount(), split.getMemberCount(), split.getPerAmount(), split.getStatus(),
                members, confirmedIds);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BigDecimal fillTotal(Long userId, Long splitId, BigDecimal totalAmount) {
        TradeSplitOrderDO split = requireSplit(splitId);
        if (!Objects.equals(split.getInitiatorId(), userId)) {
            throw exception(RESOURCE_OWNER_MISMATCH);
        }
        if (totalAmount == null || totalAmount.signum() <= 0 || totalAmount.stripTrailingZeros().scale() > 2) {
            throw exception(PARAM_INVALID);
        }
        if (!Objects.equals(split.getStatus(), 0)) {
            throw exception(RESOURCE_STATUS_INVALID);
        }
        int payerCount = Math.max(1, split.getMemberCount() - 1);
        BigDecimal per = totalAmount.setScale(2, RoundingMode.HALF_UP)
                .divide(BigDecimal.valueOf(payerCount), 2, RoundingMode.HALF_UP);
        TradeSplitOrderDO update = new TradeSplitOrderDO();
        update.setSplitId(splitId);
        update.setTotalAmount(totalAmount.setScale(2, RoundingMode.HALF_UP));
        update.setPerAmount(per);
        update.setStatus(1);
        splitOrderMapper.updateById(update);
        return per;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Integer confirm(Long userId, Long splitId, boolean confirm) {
        TradeSplitOrderDO split = requireSplit(splitId);
        List<Long> members = loadMembers(split);
        if (!members.contains(userId)) {
            throw exception(RESOURCE_OWNER_MISMATCH);
        }
        if (Objects.equals(split.getStatus(), 3) || Objects.equals(split.getStatus(), 4)) {
            return split.getStatus(); // 终态幂等
        }
        if (!Objects.equals(split.getStatus(), 1) && !Objects.equals(split.getStatus(), 2)) {
            throw exception(RESOURCE_STATUS_INVALID);
        }
        String key = confirmKey(splitId);
        if (!confirm) {
            // 一人拒绝：整单退回重算，已确认的清除
            stringRedisTemplate.delete(key);
            TradeSplitOrderDO cancelled = new TradeSplitOrderDO();
            cancelled.setSplitId(splitId);
            cancelled.setStatus(4);
            splitOrderMapper.updateById(cancelled);
            return 4;
        }
        stringRedisTemplate.opsForSet().add(key, String.valueOf(userId));
        stringRedisTemplate.expire(key, Duration.ofDays(7));

        List<Long> payerIds = members.stream()
                .filter(id -> !Objects.equals(id, split.getInitiatorId()))
                .toList();
        Set<String> confirmed = stringRedisTemplate.opsForSet().members(key);
        boolean allConfirmed = confirmed != null && confirmed.size() >= payerIds.size();
        if (!allConfirmed) {
            TradeSplitOrderDO collecting = new TradeSplitOrderDO();
            collecting.setSplitId(splitId);
            collecting.setStatus(2);
            splitOrderMapper.updateById(collecting);
            return 2;
        }
        createTransfers(split, payerIds, key);
        TradeSplitOrderDO done = new TradeSplitOrderDO();
        done.setSplitId(splitId);
        done.setStatus(3);
        splitOrderMapper.updateById(done);
        return 3;
    }

    /** 拆 N 笔 scene=2 转账；任一失败整体回滚并清空确认位，让参与人改天重试 */
    private void createTransfers(TradeSplitOrderDO split, List<Long> payerIds, String confirmKey) {
        List<BigDecimal> amounts = SplitAmountCalculator.allocate(split.getTotalAmount(), payerIds.size());
        try {
            for (int i = 0; i < payerIds.size(); i++) {
                Long payerId = payerIds.get(i);
                transferService.create(payerId,
                        new TransferService.CreateCmd(split.getInitiatorId(), null, amounts.get(i), 2,
                                "活动分摊 " + split.getSplitNo()),
                        split.getSplitNo() + ":" + payerId);
            }
        } catch (RuntimeException ex) {
            stringRedisTemplate.delete(confirmKey);
            log.warn("[createTransfers][splitNo({}) 拆单失败，整单回滚]", split.getSplitNo(), ex);
            throw ex;
        }
    }

    private TradeSplitOrderDO requireSplit(Long splitId) {
        TradeSplitOrderDO split = splitOrderMapper.selectById(splitId);
        if (split == null) {
            throw exception(RESOURCE_OWNER_MISMATCH);
        }
        return split;
    }

    /** 成员 = 发起人 + 需求单已通过报名者（与 M2 成团口径一致） */
    private List<Long> loadMembers(TradeSplitOrderDO split) {
        List<Long> members = new ArrayList<>();
        members.add(split.getInitiatorId());
        if (split.getDemandId() != null) {
            demandService.listSignups(split.getDemandId()).stream()
                    .filter(s -> Integer.valueOf(1).equals(s.getAuditStatus()))
                    .map(s -> s.getUserId())
                    .filter(id -> !members.contains(id))
                    .forEach(members::add);
        }
        return members;
    }

    private String confirmKey(Long splitId) {
        return CONFIRM_KEY_PREFIX + splitId;
    }
}
