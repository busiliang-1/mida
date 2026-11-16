package cn.iocoder.yudao.module.mate.service.match;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.mate.dal.dataobject.match.MateDemandDO;
import cn.iocoder.yudao.module.mate.dal.dataobject.match.MateSignupDO;

import java.util.List;

/**
 * M2 搭子需求单 Service。
 */
public interface DemandService {

    Long createDemand(Long userId, MateDemandDO demand);

    MateDemandDO getDemand(Long demandId);

    PageResult<MateDemandDO> pageDemand(PageParam pageParam, String cityCode, String activityType, Integer status);

    Long signup(Long userId, Long demandId, String message);

    /** 发起者审核报名，锁内重算人数触发成团。action=1通过2拒绝 */
    void reviewSignup(Long userId, Long demandId, Long signupId, Integer action);

    /** 发起者手动成团（未满员） */
    void manualFormation(Long userId, Long demandId);

    List<MateSignupDO> listSignups(Long demandId);
}
