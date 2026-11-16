package cn.iocoder.yudao.module.mate.dal.mysql.auth;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.mate.dal.dataobject.auth.AuthRecordDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AuthRecordMapper extends BaseMapperX<AuthRecordDO> {

    default AuthRecordDO selectByReportNo(String reportNo) {
        return selectOne(new LambdaQueryWrapperX<AuthRecordDO>()
                .eq(AuthRecordDO::getReportNo, reportNo));
    }

    default PageResult<AuthRecordDO> selectPage(PageParam pageParam, Integer verifyResult) {
        return selectPage(pageParam, new LambdaQueryWrapperX<AuthRecordDO>()
                .eqIfPresent(AuthRecordDO::getVerifyResult, verifyResult)
                .orderByDesc(AuthRecordDO::getRecordId));
    }
}
