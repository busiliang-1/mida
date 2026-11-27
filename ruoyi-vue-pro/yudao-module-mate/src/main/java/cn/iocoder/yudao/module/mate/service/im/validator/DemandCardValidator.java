package cn.iocoder.yudao.module.mate.service.im.validator;

import cn.iocoder.yudao.module.mate.dal.dataobject.match.MateDemandDO;
import cn.iocoder.yudao.module.mate.dal.dataobject.match.MateSignupDO;
import cn.iocoder.yudao.module.mate.enums.DemandStatusEnum;
import cn.iocoder.yudao.module.mate.enums.im.ImCardTypeEnum;
import cn.iocoder.yudao.module.mate.service.im.CardValidator;
import cn.iocoder.yudao.module.mate.service.match.DemandService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.mate.enums.ErrorCodeConstants.RESOURCE_OWNER_MISMATCH;

/**
 * 需求卡片校验器（cardType=1，bizNo=demandId）。
 * 归属：发布者或报名已通过的成员才能发这张卡；状态：草稿/审核中/已关闭的单子不允许发。
 */
@Component
public class DemandCardValidator implements CardValidator {

    /** 允许发卡的需求状态：招募中、已成团、进行中、已结束 */
    private static final Set<Integer> SENDABLE_STATUS = Set.of(
            DemandStatusEnum.RECRUITING.getStatus(), DemandStatusEnum.FORMED.getStatus(),
            DemandStatusEnum.ONGOING.getStatus(), DemandStatusEnum.FINISHED.getStatus());

    @Resource
    private DemandService demandService;

    @Override
    public boolean supports(Integer cardType) {
        return Objects.equals(ImCardTypeEnum.DEMAND.getType(), cardType);
    }

    @Override
    public void validate(Integer cardType, String bizNo, Long senderId) {
        MateDemandDO demand = load(bizNo);
        if (demand == null || !SENDABLE_STATUS.contains(demand.getStatus())) {
            throw exception(RESOURCE_OWNER_MISMATCH);
        }
        if (Objects.equals(demand.getPublisherId(), senderId)) {
            return;
        }
        boolean participant = demandService.listSignups(demand.getDemandId()).stream()
                .filter(s -> Objects.equals(s.getUserId(), senderId))
                .anyMatch(s -> Objects.equals(s.getAuditStatus(), 1));
        if (!participant) {
            throw exception(RESOURCE_OWNER_MISMATCH);
        }
    }

    @Override
    public Map<String, Object> render(Integer cardType, String bizNo) {
        MateDemandDO demand = load(bizNo);
        if (demand == null) {
            return null;
        }
        Map<String, Object> card = new LinkedHashMap<>();
        card.put("cardType", ImCardTypeEnum.DEMAND.getType());
        card.put("bizNo", String.valueOf(demand.getDemandId()));
        card.put("title", demand.getTitle());
        card.put("activityType", demand.getActivityType());
        card.put("startTime", demand.getStartTime());
        card.put("locationName", demand.getLocationName());
        card.put("needCount", demand.getNeedCount());
        card.put("joinedCount", demand.getJoinedCount());
        card.put("status", demand.getStatus());
        card.put("statusText", DemandStatusEnum.of(demand.getStatus()).getName());
        return card;
    }

    /** bizNo 非法（非数字/超长）不抛 500，统一按"单据不存在"处理 */
    private MateDemandDO load(String bizNo) {
        if (bizNo == null || bizNo.isBlank()) {
            return null;
        }
        try {
            return demandService.getDemand(Long.valueOf(bizNo));
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
