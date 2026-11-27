package cn.iocoder.yudao.module.mate.dal.mysql.moment;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.mate.dal.dataobject.moment.HomepageConfigDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * M5 主页配置 Mapper。user_id 唯一，一个用户只有一份配置。
 */
@Mapper
public interface HomepageConfigMapper extends BaseMapperX<HomepageConfigDO> {

    default HomepageConfigDO selectByUserId(Long userId) {
        return selectOne(new LambdaQueryWrapperX<HomepageConfigDO>()
                .eq(HomepageConfigDO::getUserId, userId));
    }
}
