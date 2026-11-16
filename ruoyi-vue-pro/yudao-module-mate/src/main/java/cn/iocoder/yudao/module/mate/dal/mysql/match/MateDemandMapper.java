package cn.iocoder.yudao.module.mate.dal.mysql.match;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.mate.dal.dataobject.match.MateDemandDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface MateDemandMapper extends BaseMapperX<MateDemandDO> {

    default PageResult<MateDemandDO> selectPage(PageParam pageParam, String cityCode, String activityType, Integer status) {
        return selectPage(pageParam, new LambdaQueryWrapperX<MateDemandDO>()
                .eqIfPresent(MateDemandDO::getCityCode, cityCode)
                .eqIfPresent(MateDemandDO::getActivityType, activityType)
                .eqIfPresent(MateDemandDO::getStatus, status)
                .orderByDesc(MateDemandDO::getDemandId));
    }
}
