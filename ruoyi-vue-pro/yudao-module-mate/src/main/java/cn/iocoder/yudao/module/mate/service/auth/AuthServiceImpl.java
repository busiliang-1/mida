package cn.iocoder.yudao.module.mate.service.auth;

import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.enums.UserTypeEnum;
import cn.iocoder.yudao.module.mate.dal.dataobject.auth.AuthRecordDO;
import cn.iocoder.yudao.module.mate.dal.dataobject.auth.SysUserDO;
import cn.iocoder.yudao.module.mate.dal.mysql.auth.AuthRecordMapper;
import cn.iocoder.yudao.module.mate.dal.mysql.auth.SysUserMapper;
import cn.iocoder.yudao.module.mate.enums.AuthEnums;
import cn.iocoder.yudao.module.mate.framework.crypto.PhoneCryptoService;
import cn.iocoder.yudao.module.system.dal.dataobject.oauth2.OAuth2AccessTokenDO;
import cn.iocoder.yudao.module.system.service.oauth2.OAuth2TokenService;
import jakarta.annotation.Resource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.mate.enums.ErrorCodeConstants.*;

/**
 * M1 用户与认证 Service 实现。
 * 外部核验均为替身：实名要素恒通过（格式校验），学籍转人工审核。业务状态机真实。
 */
@Service
@Validated
public class AuthServiceImpl implements AuthService {

    private static final String SMS_KEY_PREFIX = "mate:sms:";
    private static final String MOCK_SMS_CODE = "123456"; // 替身验证码
    private static final String OAUTH2_CLIENT_ID = "default";

    @Resource
    private SysUserMapper sysUserMapper;
    @Resource
    private AuthRecordMapper authRecordMapper;
    @Resource
    private PhoneCryptoService phoneCryptoService;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private OAuth2TokenService oauth2TokenService;

    @Override
    public void sendSmsCode(String phone) {
        // 替身：固定验证码，存 Redis 5 分钟。生产替换为短信服务商。
        stringRedisTemplate.opsForValue().set(SMS_KEY_PREFIX + phone, MOCK_SMS_CODE, 5, TimeUnit.MINUTES);
    }

    private void validateSmsCode(String phone, String code) {
        String cached = stringRedisTemplate.opsForValue().get(SMS_KEY_PREFIX + phone);
        if (cached == null || !cached.equals(code)) {
            throw exception(AUTH_SMS_CODE_INVALID);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long register(String phone, String code, String nickname) {
        validateSmsCode(phone, code);
        String phoneHash = phoneCryptoService.hash(phone);
        if (sysUserMapper.selectByPhoneHash(phoneHash) != null) {
            throw exception(AUTH_PHONE_REGISTERED);
        }
        SysUserDO user = SysUserDO.builder()
                .nickname(StrUtil.blankToDefault(nickname, "搭子" + RandomUtil.randomString(6)))
                .phoneCipher(phoneCryptoService.encrypt(phone))
                .phoneHash(phoneHash)
                .gender(0).authLevel(AuthEnums.AuthLevel.L1.getLevel())
                .creditScore(600).status(0)
                .build();
        sysUserMapper.insert(user);
        return user.getUserId();
    }

    @Override
    public OAuth2AccessTokenDO login(String phone, String code) {
        validateSmsCode(phone, code);
        SysUserDO user = sysUserMapper.selectByPhoneHash(phoneCryptoService.hash(phone));
        if (user == null) {
            throw exception(AUTH_USER_NOT_EXISTS);
        }
        if (user.getStatus() != null && user.getStatus() == 2) {
            throw exception(RESOURCE_OWNER_MISMATCH); // 封禁
        }
        // 复用芋道 OAuth2 体系签发 C 端（MEMBER）令牌
        return oauth2TokenService.createAccessToken(user.getUserId(),
                UserTypeEnum.MEMBER.getValue(), OAUTH2_CLIENT_ID, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long submitVerification(Long userId, Integer authType, String materialDigest,
                                   String reportNo, String realName, String idCard) {
        SysUserDO user = sysUserMapper.selectById(userId);
        if (user == null) {
            throw exception(AUTH_USER_NOT_EXISTS);
        }
        AuthRecordDO record = AuthRecordDO.builder()
                .userId(userId).authType(authType).materialDigest(materialDigest)
                .build();
        if (authType == AuthEnums.AuthType.REAL_NAME.getType()) {
            // L2 实名：替身要素核验恒通过，仅校验身份证格式
            if (StrUtil.isBlank(idCard) || !idCard.matches("^\\d{17}[\\dXx]$")) {
                throw exception(PARAM_INVALID);
            }
            record.setAuthLevel(AuthEnums.AuthLevel.L2.getLevel());
            record.setVerifyChannel("MOCK");
            record.setVerifyResult(AuthEnums.VerifyResult.PASS.getResult());
            authRecordMapper.insert(record);
            upgradeLevel(user, AuthEnums.AuthLevel.L2.getLevel(), null);
        } else if (authType == AuthEnums.AuthType.SCHOOL.getType()) {
            // L3 学校：报告编号唯一绑定，转人工审核
            if (StrUtil.isBlank(reportNo)) {
                throw exception(PARAM_INVALID);
            }
            if (authRecordMapper.selectByReportNo(reportNo) != null) {
                throw exception(AUTH_REPORT_NO_BOUND);
            }
            record.setAuthLevel(AuthEnums.AuthLevel.L3.getLevel());
            record.setReportNo(reportNo);
            record.setVerifyChannel("MANUAL");
            record.setVerifyResult(AuthEnums.VerifyResult.MANUAL.getResult());
            authRecordMapper.insert(record);
        } else {
            throw exception(PARAM_INVALID);
        }
        return record.getRecordId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reviewAuthRecord(Long recordId, Integer action, String operator, Integer schoolId) {
        AuthRecordDO record = authRecordMapper.selectById(recordId);
        if (record == null) {
            throw exception(AUTH_RECORD_NOT_EXISTS);
        }
        if (record.getVerifyResult() != AuthEnums.VerifyResult.MANUAL.getResult()) {
            throw exception(RESOURCE_STATUS_INVALID); // 非待人工不可审核
        }
        AuthRecordDO update = new AuthRecordDO();
        update.setRecordId(recordId);
        update.setOperator(operator);
        if (action == 1) {
            update.setVerifyResult(AuthEnums.VerifyResult.PASS.getResult());
            update.setExpireAt(LocalDateTime.now().plusDays(30));
            authRecordMapper.updateById(update);
            SysUserDO user = sysUserMapper.selectById(record.getUserId());
            upgradeLevel(user, record.getAuthLevel(), schoolId);
        } else {
            update.setVerifyResult(AuthEnums.VerifyResult.FAIL.getResult());
            authRecordMapper.updateById(update);
        }
    }

    /** 等级只升不降 */
    private void upgradeLevel(SysUserDO user, int targetLevel, Integer schoolId) {
        if (user.getAuthLevel() != null && user.getAuthLevel() >= targetLevel) {
            return;
        }
        SysUserDO update = new SysUserDO();
        update.setUserId(user.getUserId());
        update.setAuthLevel(targetLevel);
        if (schoolId != null) {
            update.setSchoolId(schoolId);
        }
        sysUserMapper.updateById(update);
    }

    @Override
    public SysUserDO getUser(Long userId) {
        return sysUserMapper.selectById(userId);
    }

    @Override
    public Integer getAuthLevel(Long userId) {
        SysUserDO user = sysUserMapper.selectById(userId);
        return user == null ? null : user.getAuthLevel();
    }

    @Override
    public AuthRecordDO getAuthRecord(Long recordId) {
        return authRecordMapper.selectById(recordId);
    }
}
