package cn.iocoder.yudao.module.mate.enums;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 需求单 7 态状态机跃迁白名单测试（判定表驱动）。
 */
class DemandStatusEnumTest {

    @Test
    void legalTransitions() {
        assertTrue(DemandStatusEnum.canTransition(0, 1)); // 草稿→审核中
        assertTrue(DemandStatusEnum.canTransition(1, 2)); // 审核中→招募中
        assertTrue(DemandStatusEnum.canTransition(2, 3)); // 招募中→已成团
        assertTrue(DemandStatusEnum.canTransition(3, 4)); // 已成团→进行中
        assertTrue(DemandStatusEnum.canTransition(3, 2)); // 已成团→回招募中(补员)
        assertTrue(DemandStatusEnum.canTransition(4, 5)); // 进行中→已结束
        assertTrue(DemandStatusEnum.canTransition(2, 6)); // 招募中→关闭(到期)
    }

    @Test
    void illegalTransitions() {
        assertFalse(DemandStatusEnum.canTransition(2, 4)); // 招募中不能直接进行中
        assertFalse(DemandStatusEnum.canTransition(3, 5)); // 已成团不能直接已结束
        assertFalse(DemandStatusEnum.canTransition(6, 2)); // 已关闭不能回招募中
        assertFalse(DemandStatusEnum.canTransition(5, 3)); // 已结束不能回已成团
        assertFalse(DemandStatusEnum.canTransition(0, 3)); // 草稿不能直接成团
    }

    @Test
    void ofUnknownThrows() {
        assertThrows(IllegalArgumentException.class, () -> DemandStatusEnum.of(99));
    }
}
