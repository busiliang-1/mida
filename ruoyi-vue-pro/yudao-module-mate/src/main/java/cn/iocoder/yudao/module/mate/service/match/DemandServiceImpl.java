package cn.iocoder.yudao.module.mate.service.match;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.mate.dal.dataobject.auth.SysUserDO;
import cn.iocoder.yudao.module.mate.dal.dataobject.match.MateDemandDO;
import cn.iocoder.yudao.module.mate.dal.dataobject.match.MateSignupDO;
import cn.iocoder.yudao.module.mate.dal.mysql.auth.SysUserMapper;
import cn.iocoder.yudao.module.mate.dal.mysql.match.MateDemandMapper;
import cn.iocoder.yudao.module.mate.dal.mysql.match.MateSignupMapper;
import cn.iocoder.yudao.module.mate.enums.DemandStatusEnum;
import cn.iocoder.yudao.module.mate.event.DemandFormedEvent;
import jakarta.annotation.Resource;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.time.LocalDateTime;
import java.util.List;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.mate.enums.ErrorCodeConstants.*;

@Service
@Validated
public class DemandServiceImpl implements DemandService {

    @Resource
    private MateDemandMapper demandMapper;
    @Resource
    private MateSignupMapper signupMapper;
    @Resource
    private SysUserMapper sysUserMapper;
    @Resource
    private RedissonClient redissonClient;
    @Resource
    private ApplicationEventPublisher eventPublisher;

    @Override
    public Long createDemand(Long userId, MateDemandDO demand) {
        if (demand.getNeedCount() == null || demand.getNeedCount() < 2 || demand.getNeedCount() > 20) {
            throw exception(PARAM_INVALID);
        }
        if (demand.getStartTime() == null || demand.getStartTime().isBefore(LocalDateTime.now().plusMinutes(10))) {
            throw exception(PARAM_INVALID);
        }
        demand.setPublisherId(userId);
        demand.setJoinedCount(1);
        // 一期内容审核(M6)接入后改为 AUDITING→审核通过→RECRUITING；当前先自动通过便于闭环
        demand.setStatus(DemandStatusEnum.RECRUITING.getStatus());
        demandMapper.insert(demand);
        return demand.getDemandId();
    }

    @Override
    public MateDemandDO getDemand(Long demandId) {
        MateDemandDO demand = demandMapper.selectById(demandId);
        if (demand == null) {
            throw exception(DEMAND_NOT_EXISTS);
        }
        return demand;
    }

    @Override
    public PageResult<MateDemandDO> pageDemand(PageParam pageParam, String cityCode, String activityType, Integer status) {
        return demandMapper.selectPage(pageParam, cityCode, activityType, status);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long signup(Long userId, Long demandId, String message) {
        MateDemandDO demand = getDemand(demandId);
        if (demand.getStatus() != DemandStatusEnum.RECRUITING.getStatus()) {
            throw exception(DEMAND_STATUS_INVALID);
        }
        if (demand.getJoinedCount() >= demand.getNeedCount()) {
            throw exception(DEMAND_FULL);
        }
        SysUserDO user = sysUserMapper.selectById(userId);
        if (user == null || user.getAuthLevel() < demand.getAuthLimit()) {
            throw exception(AUTH_LEVEL_INSUFFICIENT);
        }
        if (demand.getGenderLimit() != 0 && !demand.getGenderLimit().equals(user.getGender())) {
            throw exception(PARAM_INVALID);
        }
        if (signupMapper.selectByDemandAndUser(demandId, userId) != null) {
            throw exception(SIGNUP_DUPLICATE);
        }
        MateSignupDO signup = MateSignupDO.builder()
                .demandId(demandId).userId(userId).message(message)
                .auditStatus(0).creditSnapshot(user.getCreditScore())
                .build();
        signupMapper.insert(signup);
        return signup.getSignupId();
    }

    @Override
    public void reviewSignup(Long userId, Long demandId, Long signupId, Integer action) {
        MateDemandDO demand = getDemand(demandId);
        if (!demand.getPublisherId().equals(userId)) {
            throw exception(RESOURCE_OWNER_MISMATCH);
        }
        RLock lock = redissonClient.getLock("lock:signup:" + demandId);
        lock.lock();
        try {
            MateSignupDO signup = signupMapper.selectById(signupId);
            if (signup == null || !signup.getDemandId().equals(demandId)) {
                throw exception(SIGNUP_NOT_EXISTS);
            }
            if (action == 1) {
                // 锁内重读需求单，校验状态后重算已通过人数，防超卖
                MateDemandDO fresh = demandMapper.selectById(demandId);
                if (fresh.getStatus() != DemandStatusEnum.RECRUITING.getStatus()) {
                    throw exception(DEMAND_STATUS_INVALID);
                }
                MateSignupDO up = new MateSignupDO();
                up.setSignupId(signupId);
                up.setAuditStatus(1);
                signupMapper.updateById(up);
                long passed = signupMapper.selectPassedCount(demandId);
                int joined = (int) passed + 1; // +1 发布者本人（joinedCount 初始含发布者）
                MateDemandDO dup = new MateDemandDO();
                dup.setDemandId(demandId);
                dup.setJoinedCount(joined);
                if (joined >= fresh.getNeedCount()) {
                    if (!DemandStatusEnum.canTransition(fresh.getStatus(), DemandStatusEnum.FORMED.getStatus())) {
                        throw exception(DEMAND_STATUS_INVALID);
                    }
                    dup.setStatus(DemandStatusEnum.FORMED.getStatus());
                }
                demandMapper.updateById(dup);
                if (dup.getStatus() != null && dup.getStatus() == DemandStatusEnum.FORMED.getStatus()) {
                    eventPublisher.publishEvent(new DemandFormedEvent(demandId, fresh.getPublisherId(),
                            fresh.getNeedCount(), fresh.getFeeMode()));
                }
            } else {
                MateSignupDO up = new MateSignupDO();
                up.setSignupId(signupId);
                up.setAuditStatus(2);
                signupMapper.updateById(up);
            }
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void manualFormation(Long userId, Long demandId) {
        MateDemandDO demand = getDemand(demandId);
        if (!demand.getPublisherId().equals(userId)) {
            throw exception(RESOURCE_OWNER_MISMATCH);
        }
        RLock lock = redissonClient.getLock("lock:signup:" + demandId);
        lock.lock();
        try {
            MateDemandDO fresh = demandMapper.selectById(demandId);
            if (!DemandStatusEnum.canTransition(fresh.getStatus(), DemandStatusEnum.FORMED.getStatus())) {
                throw exception(DEMAND_STATUS_INVALID);
            }
            MateDemandDO dup = new MateDemandDO();
            dup.setDemandId(demandId);
            dup.setStatus(DemandStatusEnum.FORMED.getStatus());
            demandMapper.updateById(dup);
            eventPublisher.publishEvent(new DemandFormedEvent(demandId, fresh.getPublisherId(),
                    fresh.getNeedCount(), fresh.getFeeMode()));
        } finally {
            lock.unlock();
        }
    }

    @Override
    public List<MateSignupDO> listSignups(Long demandId) {
        return signupMapper.selectListByDemand(demandId);
    }
}
