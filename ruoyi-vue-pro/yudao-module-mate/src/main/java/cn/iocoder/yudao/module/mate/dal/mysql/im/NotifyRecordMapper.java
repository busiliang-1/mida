package cn.iocoder.yudao.module.mate.dal.mysql.im;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.mate.dal.dataobject.im.NotifyRecordDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface NotifyRecordMapper extends BaseMapperX<NotifyRecordDO> {

    default Long countUnread(Long userId) {
        return selectCount(new LambdaQueryWrapperX<NotifyRecordDO>()
                .eq(NotifyRecordDO::getUserId, userId)
                .eq(NotifyRecordDO::getReadFlag, 0));
    }
}
