package cn.iocoder.yudao.module.mate.dal.mysql.trade;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.mate.dal.dataobject.trade.TradePayNotifyDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface TradePayNotifyMapper extends BaseMapperX<TradePayNotifyDO> {

    default TradePayNotifyDO selectByEventId(String eventId) {
        return selectOne(new LambdaQueryWrapperX<TradePayNotifyDO>()
                .eq(TradePayNotifyDO::getEventId, eventId));
    }
}
