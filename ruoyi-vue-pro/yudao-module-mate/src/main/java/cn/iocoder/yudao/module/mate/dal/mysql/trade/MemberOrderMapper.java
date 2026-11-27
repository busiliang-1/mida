package cn.iocoder.yudao.module.mate.dal.mysql.trade;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.mate.dal.dataobject.trade.MemberOrderDO;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDateTime;

@Mapper
public interface MemberOrderMapper extends BaseMapperX<MemberOrderDO> {

    default MemberOrderDO selectByNo(String orderNo) {
        return selectOne(new LambdaQueryWrapperX<MemberOrderDO>()
                .eq(MemberOrderDO::getOrderNo, orderNo));
    }

    /** 当前生效的会员订单（续费基准取它未过期的 expire_at） */
    default MemberOrderDO selectLatestActive(Long userId, LocalDateTime now) {
        return selectFirstOne(new LambdaQueryWrapperX<MemberOrderDO>()
                .eq(MemberOrderDO::getUserId, userId)
                .eq(MemberOrderDO::getOrderType, 1)
                .eq(MemberOrderDO::getStatus, 2)
                .gt(MemberOrderDO::getExpireAt, now)
                .orderByDesc(MemberOrderDO::getExpireAt));
    }
}
