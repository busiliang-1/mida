package cn.iocoder.yudao.module.mate.dal.mysql.trade;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.mate.dal.dataobject.trade.WalletFlowDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface WalletFlowMapper extends BaseMapperX<WalletFlowDO> {

    default List<WalletFlowDO> selectListByTransfer(Long transferId) {
        return selectList(new LambdaQueryWrapperX<WalletFlowDO>()
                .eq(WalletFlowDO::getTransferId, transferId));
    }

    default WalletFlowDO selectLastByUser(Long userId) {
        return selectFirstOne(new LambdaQueryWrapperX<WalletFlowDO>()
                .eq(WalletFlowDO::getUserId, userId)
                .orderByDesc(WalletFlowDO::getCreateTime)
                .orderByDesc(WalletFlowDO::getFlowId));
    }
}
