package cn.iocoder.yudao.module.mate.dal.mysql.risk;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.mate.dal.dataobject.risk.RiskReportDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface RiskReportMapper extends BaseMapperX<RiskReportDO> {

    default PageResult<RiskReportDO> selectPage(PageParam pageParam, Integer status) {
        return selectPage(pageParam, new LambdaQueryWrapperX<RiskReportDO>()
                .eqIfPresent(RiskReportDO::getStatus, status)
                .orderByDesc(RiskReportDO::getReportId));
    }
}
