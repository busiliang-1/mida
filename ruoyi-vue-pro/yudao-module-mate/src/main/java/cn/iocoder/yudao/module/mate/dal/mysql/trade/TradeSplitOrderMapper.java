package cn.iocoder.yudao.module.mate.dal.mysql.trade;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.mate.dal.dataobject.trade.TradeSplitOrderDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface TradeSplitOrderMapper extends BaseMapperX<TradeSplitOrderDO> {

    default TradeSplitOrderDO selectByDemandId(Long demandId) {
        return selectOne(new LambdaQueryWrapperX<TradeSplitOrderDO>()
                .eq(TradeSplitOrderDO::getDemandId, demandId));
    }
}
