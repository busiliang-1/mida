package cn.iocoder.yudao.module.mate.dal.mysql.match;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.mate.dal.dataobject.match.MateSignupDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface MateSignupMapper extends BaseMapperX<MateSignupDO> {

    default MateSignupDO selectByDemandAndUser(Long demandId, Long userId) {
        return selectOne(new LambdaQueryWrapperX<MateSignupDO>()
                .eq(MateSignupDO::getDemandId, demandId)
                .eq(MateSignupDO::getUserId, userId));
    }

    default Long selectPassedCount(Long demandId) {
        return selectCount(new LambdaQueryWrapperX<MateSignupDO>()
                .eq(MateSignupDO::getDemandId, demandId)
                .eq(MateSignupDO::getAuditStatus, 1));
    }

    default List<MateSignupDO> selectListByDemand(Long demandId) {
        return selectList(new LambdaQueryWrapperX<MateSignupDO>()
                .eq(MateSignupDO::getDemandId, demandId)
                .orderByDesc(MateSignupDO::getSignupId));
    }
}
