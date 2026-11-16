package cn.iocoder.yudao.module.mate.dal.mysql.risk;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.mate.dal.dataobject.risk.RiskEventDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface RiskEventMapper extends BaseMapperX<RiskEventDO> {

    default List<RiskEventDO> selectListByUser(Long userId) {
        return selectList(new LambdaQueryWrapperX<RiskEventDO>()
                .eq(RiskEventDO::getUserId, userId)
                .orderByDesc(RiskEventDO::getEventId));
    }
}
