package cn.iocoder.yudao.module.mate.dal.mysql.risk;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.mate.dal.dataobject.risk.RiskBlacklistDO;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDateTime;

@Mapper
public interface RiskBlacklistMapper extends BaseMapperX<RiskBlacklistDO> {

    default boolean existsActiveByUser(Long userId) {
        Long count = selectCount(new LambdaQueryWrapperX<RiskBlacklistDO>()
                .eq(RiskBlacklistDO::getUserId, userId)
                .and(w -> w.isNull(RiskBlacklistDO::getExpireAt)
                        .or().gt(RiskBlacklistDO::getExpireAt, LocalDateTime.now())));
        return count != null && count > 0;
    }
}
