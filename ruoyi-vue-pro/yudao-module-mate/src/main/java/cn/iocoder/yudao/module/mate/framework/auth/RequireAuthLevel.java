package cn.iocoder.yudao.module.mate.framework.auth;

import java.lang.annotation.*;

/**
 * 声明接口所需的最低认证等级。由 {@link AuthLevelAspect} 统一校验，
 * 业务代码不再写 if(authLevel >= x)。不足返回 1003。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequireAuthLevel {

    /** 最低认证等级，如 2=实名、3=学校 */
    int value();
}
