package cn.iocoder.yudao.module.mate.dal.mysql.match;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.mate.dal.dataobject.match.MateMatchDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface MateMatchMapper extends BaseMapperX<MateMatchDO> {

    default MateMatchDO selectByLowHigh(Long lowId, Long highId) {
        return selectOne(new LambdaQueryWrapperX<MateMatchDO>()
                .eq(MateMatchDO::getUserLowId, lowId)
                .eq(MateMatchDO::getUserHighId, highId));
    }
}
