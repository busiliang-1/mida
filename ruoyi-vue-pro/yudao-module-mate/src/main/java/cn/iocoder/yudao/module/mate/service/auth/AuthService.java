package cn.iocoder.yudao.module.mate.service.auth;

import cn.iocoder.yudao.module.mate.dal.dataobject.auth.AuthRecordDO;
import cn.iocoder.yudao.module.mate.dal.dataobject.auth.SysUserDO;
import cn.iocoder.yudao.module.system.dal.dataobject.oauth2.OAuth2AccessTokenDO;
import jakarta.validation.constraints.NotBlank;

/**
 * M1 用户与认证 Service。
 */
public interface AuthService {

    /** 发送短信验证码（替身：固定 123456，存 Redis） */
    void sendSmsCode(String phone);

    /** 手机号注册，建立 L1，返回用户 ID */
    Long register(String phone, String code, String nickname);

    /** 手机号登录，签发 C 端令牌 */
    OAuth2AccessTokenDO login(String phone, String code);

    /** 提交认证：authType=1 实名(L2,替身自动通过) / 2 学校(L3,转人工) */
    Long submitVerification(Long userId, Integer authType, String materialDigest,
                            String reportNo, String realName, String idCard);

    /** 后台审核认证记录：action=1 通过 / 2 驳回 */
    void reviewAuthRecord(Long recordId, Integer action, String operator, Integer schoolId);

    /** 查用户 */
    SysUserDO getUser(Long userId);

    /** 查认证等级（供切面/其他模块） */
    Integer getAuthLevel(Long userId);

    /** 查认证记录 */
    AuthRecordDO getAuthRecord(Long recordId);
}
