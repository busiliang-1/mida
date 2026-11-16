package cn.iocoder.yudao.module.mate.framework.auth;

import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.mate.dal.dataobject.auth.SysUserDO;
import cn.iocoder.yudao.module.mate.dal.mysql.auth.SysUserMapper;
import jakarta.annotation.Resource;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Component;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.mate.enums.ErrorCodeConstants.AUTH_LEVEL_INSUFFICIENT;
import static cn.iocoder.yudao.module.mate.enums.ErrorCodeConstants.AUTH_USER_NOT_EXISTS;

/**
 * 认证等级校验切面。读取当前登录用户的 auth_level，低于注解要求则拒绝。
 */
@Aspect
@Component
public class AuthLevelAspect {

    @Resource
    private SysUserMapper sysUserMapper;

    @Before("@annotation(requireAuthLevel)")
    public void before(RequireAuthLevel requireAuthLevel) {
        Long userId = SecurityFrameworkUtils.getLoginUserId();
        if (userId == null) {
            throw exception(AUTH_LEVEL_INSUFFICIENT);
        }
        SysUserDO user = sysUserMapper.selectById(userId);
        if (user == null) {
            throw exception(AUTH_USER_NOT_EXISTS);
        }
        if (user.getAuthLevel() == null || user.getAuthLevel() < requireAuthLevel.value()) {
            throw exception(AUTH_LEVEL_INSUFFICIENT);
        }
    }
}
