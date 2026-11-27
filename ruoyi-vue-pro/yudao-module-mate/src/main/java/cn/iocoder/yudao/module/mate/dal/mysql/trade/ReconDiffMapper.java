package cn.iocoder.yudao.module.mate.dal.mysql.trade;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.mate.dal.dataobject.trade.ReconDiffDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface ReconDiffMapper extends BaseMapperX<ReconDiffDO> {

    default List<ReconDiffDO> selectListByStatus(Integer status, Long beforeId, int limit) {
        return selectListLimitN(new LambdaQueryWrapperX<ReconDiffDO>()
                .eqIfPresent(ReconDiffDO::getStatus, status)
                .ltIfPresent(ReconDiffDO::getId, beforeId)
                .orderByDesc(ReconDiffDO::getId), limit);
    }
}
