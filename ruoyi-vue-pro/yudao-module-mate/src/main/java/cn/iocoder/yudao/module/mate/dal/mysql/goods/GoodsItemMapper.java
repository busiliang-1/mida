package cn.iocoder.yudao.module.mate.dal.mysql.goods;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.mate.dal.dataobject.goods.GoodsItemDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface GoodsItemMapper extends BaseMapperX<GoodsItemDO> {

    default List<GoodsItemDO> selectOnSale(String category, Long beforeId, int limit) {
        return selectListLimitN(new LambdaQueryWrapperX<GoodsItemDO>()
                .eq(GoodsItemDO::getStatus, 0)
                .eqIfPresent(GoodsItemDO::getCategory, category)
                .ltIfPresent(GoodsItemDO::getGoodsId, beforeId)
                .orderByDesc(GoodsItemDO::getGoodsId), limit);
    }

    /**
     * 下单锁定：status + version 双条件，影响 0 行即"已被别人买走"。
     * 原生 SQL 必须显式带 deleted，@TableLogic 不会自动拼到 @Update 里。
     */
    @Update("UPDATE goods_item SET status = 1, version = version + 1, update_time = NOW() "
            + "WHERE goods_id = #{goodsId} AND status = 0 AND version = #{version} AND deleted = b'0'")
    int lock(@Param("goodsId") Long goodsId, @Param("version") Integer version);

    @Update("UPDATE goods_item SET status = 2, version = version + 1, update_time = NOW() "
            + "WHERE goods_id = #{goodsId} AND status = 1 AND deleted = b'0'")
    int markSold(@Param("goodsId") Long goodsId);

    @Update("UPDATE goods_item SET status = 3, version = version + 1, update_time = NOW() "
            + "WHERE goods_id = #{goodsId} AND status = 0 AND seller_id = #{sellerId} AND deleted = b'0'")
    int offShelf(@Param("goodsId") Long goodsId, @Param("sellerId") Long sellerId);
}
