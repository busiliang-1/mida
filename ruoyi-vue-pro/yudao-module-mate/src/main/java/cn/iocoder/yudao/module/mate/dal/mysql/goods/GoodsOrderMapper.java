package cn.iocoder.yudao.module.mate.dal.mysql.goods;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.mate.dal.dataobject.goods.GoodsOrderDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface GoodsOrderMapper extends BaseMapperX<GoodsOrderDO> {

    default GoodsOrderDO selectByNo(String orderNo) {
        return selectOne(new LambdaQueryWrapperX<GoodsOrderDO>()
                .eq(GoodsOrderDO::getOrderNo, orderNo));
    }

    /** 同一买家只能有一个有效订单（status=0 待确认） */
    default Long countActiveByBuyer(Long buyerId) {
        return selectCount(new LambdaQueryWrapperX<GoodsOrderDO>()
                .eq(GoodsOrderDO::getBuyerId, buyerId)
                .eq(GoodsOrderDO::getStatus, 0));
    }

    /** role=1 买入（buyer），2 卖出（seller） */
    default List<GoodsOrderDO> selectMyOrders(Long userId, Integer role, Long beforeId, int limit) {
        LambdaQueryWrapperX<GoodsOrderDO> wrapper = new LambdaQueryWrapperX<>();
        if (Integer.valueOf(2).equals(role)) {
            wrapper.eq(GoodsOrderDO::getSellerId, userId);
        } else {
            wrapper.eq(GoodsOrderDO::getBuyerId, userId);
        }
        wrapper.ltIfPresent(GoodsOrderDO::getOrderId, beforeId);
        return selectListLimitN(wrapper.orderByDesc(GoodsOrderDO::getOrderId), limit);
    }

    /** 状态机 CAS：只有待确认能变已完成 */
    @Update("UPDATE goods_order SET status = 1, finished_at = NOW(), update_time = NOW() "
            + "WHERE order_id = #{orderId} AND status = 0 AND deleted = b'0'")
    int complete(@Param("orderId") Long orderId);
}
