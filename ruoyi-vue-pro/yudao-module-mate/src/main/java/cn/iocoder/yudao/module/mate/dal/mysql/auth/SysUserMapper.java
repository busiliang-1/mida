package cn.iocoder.yudao.module.mate.dal.mysql.auth;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.mate.dal.dataobject.auth.SysUserDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SysUserMapper extends BaseMapperX<SysUserDO> {

    default SysUserDO selectByPhoneHash(String phoneHash) {
        return selectOne(new LambdaQueryWrapperX<SysUserDO>()
                .eq(SysUserDO::getPhoneHash, phoneHash));
    }
}
