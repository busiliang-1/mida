package cn.iocoder.yudao.module.mate.dal.mysql.match;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.mate.dal.dataobject.match.MateLikeDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface MateLikeMapper extends BaseMapperX<MateLikeDO> {

    default MateLikeDO selectByLikerAndLikee(Long likerId, Long likeeId) {
        return selectOne(new LambdaQueryWrapperX<MateLikeDO>()
                .eq(MateLikeDO::getLikerId, likerId)
                .eq(MateLikeDO::getLikeeId, likeeId));
    }
}
