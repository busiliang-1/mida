package cn.iocoder.yudao.module.mate.dal.mysql.trade;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.mate.dal.dataobject.trade.TradePayNotifyDO;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface TradePayNotifyMapper extends BaseMapperX<TradePayNotifyDO> {

    default TradePayNotifyDO selectByEventId(String eventId) {
        return selectOne(new LambdaQueryWrapperX<TradePayNotifyDO>()
                .eq(TradePayNotifyDO::getEventId, eventId));
    }

    /** 对账用：取时段内支付成功的回调（渠道侧账本） */
    default List<TradePayNotifyDO> selectSuccessBetween(LocalDateTime from, LocalDateTime to) {
        return selectList(new LambdaQueryWrapperX<TradePayNotifyDO>()
                .eq(TradePayNotifyDO::getStatus, "SUCCESS")
                .ge(TradePayNotifyDO::getHandledAt, from)
                .lt(TradePayNotifyDO::getHandledAt, to));
    }
}
