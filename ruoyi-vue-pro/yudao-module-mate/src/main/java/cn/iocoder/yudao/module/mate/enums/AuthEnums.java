package cn.iocoder.yudao.module.mate.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 认证相关枚举集合。
 */
public final class AuthEnums {

    /** 认证等级：L1 手机号 / L2 实名 / L3 学校（解锁卖二手）。L4 活体一期不做。 */
    @Getter
    @AllArgsConstructor
    public enum AuthLevel {
        L1(1, "手机号"), L2(2, "实名"), L3(3, "学校");
        private final int level;
        private final String name;
    }

    /** 认证类型：1 实名(L2) / 2 学校(L3) */
    @Getter
    @AllArgsConstructor
    public enum AuthType {
        REAL_NAME(1, 2, "实名"), SCHOOL(2, 3, "学校");
        private final int type;
        private final int targetLevel;
        private final String name;
    }

    /** 核验结论：1 通过 / 2 不通过 / 3 待人工 */
    @Getter
    @AllArgsConstructor
    public enum VerifyResult {
        PASS(1), FAIL(2), MANUAL(3);
        private final int result;
    }
}
