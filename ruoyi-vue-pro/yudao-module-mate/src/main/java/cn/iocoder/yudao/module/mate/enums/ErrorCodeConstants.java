package cn.iocoder.yudao.module.mate.enums;

import cn.iocoder.yudao.framework.common.exception.ErrorCode;

/**
 * Mate 模块错误码常量。
 * 码值遵循《接口与测试速查.md》统一错误码表，跨模块共用。
 */
public interface ErrorCodeConstants {

    // ========== 通用 1001-1004 ==========
    ErrorCode PARAM_INVALID = new ErrorCode(1001, "参数校验失败");
    ErrorCode UNAUTHORIZED = new ErrorCode(1002, "未登录或登录已过期");
    ErrorCode AUTH_LEVEL_INSUFFICIENT = new ErrorCode(1003, "认证等级不足");
    ErrorCode RESOURCE_OWNER_MISMATCH = new ErrorCode(1004, "权限不足或资源归属不符");

    // ========== 幂等 / 状态 2001-2002 ==========
    ErrorCode IDEMPOTENT_DUPLICATE = new ErrorCode(2001, "重复请求，已返回首次结果");
    ErrorCode RESOURCE_STATUS_INVALID = new ErrorCode(2002, "资源状态不允许当前操作");

    // ========== 资金 / 风控 3001-3003 ==========
    ErrorCode BALANCE_INSUFFICIENT = new ErrorCode(3001, "余额不足");
    ErrorCode QUOTA_EXCEEDED = new ErrorCode(3002, "超出限额或频次限制");
    ErrorCode RISK_BLOCKED = new ErrorCode(3003, "风控拦截");

    // ========== 内容 4001 ==========
    ErrorCode CONTENT_AUDIT_HIT = new ErrorCode(4001, "内容命中审核规则");

    // ========== 支付 5001 ==========
    ErrorCode PAY_SIGN_INVALID = new ErrorCode(5001, "支付回调验签失败");

    // ========== M1 认证 ==========
    ErrorCode AUTH_USER_NOT_EXISTS = new ErrorCode(1001001, "用户不存在");
    ErrorCode AUTH_SMS_CODE_INVALID = new ErrorCode(1001002, "验证码错误或已过期");
    ErrorCode AUTH_PHONE_REGISTERED = new ErrorCode(1001003, "手机号已注册");
    ErrorCode AUTH_REPORT_NO_BOUND = new ErrorCode(2002001, "该学籍报告编号已绑定其他账号");
    ErrorCode AUTH_RECORD_NOT_EXISTS = new ErrorCode(1001004, "认证记录不存在");

    // ========== M2 匹配 ==========
    ErrorCode DEMAND_NOT_EXISTS = new ErrorCode(1002001, "搭子需求单不存在");
    ErrorCode DEMAND_STATUS_INVALID = new ErrorCode(2002002, "需求单状态不允许当前操作");
    ErrorCode DEMAND_FULL = new ErrorCode(2002003, "需求单已满员");
    ErrorCode SIGNUP_DUPLICATE = new ErrorCode(2002004, "请勿重复报名");
    ErrorCode SIGNUP_NOT_EXISTS = new ErrorCode(1002002, "报名记录不存在");
    ErrorCode MATCH_QUOTA_EXCEEDED = new ErrorCode(3002001, "今日滑动次数已用尽");

    // ========== M6 风控 ==========
    ErrorCode REPORT_NOT_EXISTS = new ErrorCode(1006001, "举报工单不存在");
}
