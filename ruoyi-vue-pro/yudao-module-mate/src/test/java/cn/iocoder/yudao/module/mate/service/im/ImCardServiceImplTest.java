package cn.iocoder.yudao.module.mate.service.im;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 卡片防伪入口单测：未接入校验器的类型、非法类型一律拒绝；渲染查不到返回 null（空态）。
 */
class ImCardServiceImplTest {

    private final ImCardServiceImpl cardService = new ImCardServiceImpl();

    /** 只支持转账卡(2)的桩校验器：归属不符直接 1004 */
    private final CardValidator transferStub = new CardValidator() {
        @Override
        public boolean supports(Integer cardType) {
            return Integer.valueOf(2).equals(cardType);
        }

        @Override
        public void validate(Integer cardType, String bizNo, Long senderId) {
            if (!"T-1".equals(bizNo)) {
                throw new ServiceException(1004, "权限不足或资源归属不符");
            }
        }

        @Override
        public Map<String, Object> render(Integer cardType, String bizNo) {
            return "T-1".equals(bizNo) ? Map.of("bizNo", bizNo) : null;
        }
    };

    @Test
    void rejectUnknownCardType() {
        ReflectionTestUtils.setField(cardService, "validators", List.of(transferStub));

        ServiceException ex = assertThrows(ServiceException.class,
                () -> cardService.validateOrThrow(9, "X-1", 1L));

        assertEquals(1001, ex.getCode());
    }

    @Test
    void rejectCardTypeWithoutValidator() {
        ReflectionTestUtils.setField(cardService, "validators", List.of(transferStub));

        // 二手卡(3)在 M7 未接入前不允许发送，避免未校验的单据进会话
        ServiceException ex = assertThrows(ServiceException.class,
                () -> cardService.validateOrThrow(3, "G-1", 1L));

        assertEquals(1004, ex.getCode());
    }

    @Test
    void forgedBizNoRejected() {
        ReflectionTestUtils.setField(cardService, "validators", List.of(transferStub));

        ServiceException ex = assertThrows(ServiceException.class,
                () -> cardService.validateOrThrow(2, "T-999", 1L));

        assertEquals(1004, ex.getCode());
    }

    @Test
    void renderMissingBillReturnsNull() {
        ReflectionTestUtils.setField(cardService, "validators", List.of(transferStub));

        assertNull(cardService.render(2, "T-404"));
        assertEquals("T-1", cardService.render(2, "T-1").get("bizNo"));
    }
}
