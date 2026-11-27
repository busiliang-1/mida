package cn.iocoder.yudao.module.mate.service.im.validator;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.mate.dal.dataobject.match.MateDemandDO;
import cn.iocoder.yudao.module.mate.dal.dataobject.match.MateSignupDO;
import cn.iocoder.yudao.module.mate.service.match.DemandService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

/**
 * 需求卡片归属校验：非发布者/非已通过报名者，或单据状态不可发卡，都必须 1004。
 */
@ExtendWith(MockitoExtension.class)
class DemandCardValidatorTest {

    private static final Long DEMAND_ID = 8001L;
    private static final Long PUBLISHER = 11L;
    private static final Long PARTICIPANT = 12L;
    private static final Long STRANGER = 13L;

    @Mock
    private DemandService demandService;

    @InjectMocks
    private DemandCardValidator validator;

    private MateDemandDO demand(int status) {
        return MateDemandDO.builder().demandId(DEMAND_ID).publisherId(PUBLISHER)
                .title("周末打球").activityType("运动").needCount(4).joinedCount(2).status(status).build();
    }

    @Test
    void publisherCanSendRecruitingCard() {
        when(demandService.getDemand(DEMAND_ID)).thenReturn(demand(2));

        validator.validate(1, String.valueOf(DEMAND_ID), PUBLISHER);
    }

    @Test
    void approvedSignupCanSendCard() {
        when(demandService.getDemand(DEMAND_ID)).thenReturn(demand(3));
        when(demandService.listSignups(DEMAND_ID)).thenReturn(List.of(
                MateSignupDO.builder().signupId(1L).demandId(DEMAND_ID).userId(PARTICIPANT).auditStatus(1).build()));

        validator.validate(1, String.valueOf(DEMAND_ID), PARTICIPANT);
    }

    @Test
    void strangerRejected() {
        when(demandService.getDemand(DEMAND_ID)).thenReturn(demand(3));
        when(demandService.listSignups(DEMAND_ID)).thenReturn(List.of());

        ServiceException ex = assertThrows(ServiceException.class,
                () -> validator.validate(1, String.valueOf(DEMAND_ID), STRANGER));

        assertEquals(1004, ex.getCode());
    }

    @Test
    void closedDemandRejected() {
        when(demandService.getDemand(DEMAND_ID)).thenReturn(demand(6));

        ServiceException ex = assertThrows(ServiceException.class,
                () -> validator.validate(1, String.valueOf(DEMAND_ID), PUBLISHER));

        assertEquals(1004, ex.getCode());
    }

    @Test
    void nonNumericBizNoRejectedWithout500() {
        ServiceException ex = assertThrows(ServiceException.class,
                () -> validator.validate(1, "DEMAND-X", PUBLISHER));

        assertEquals(1004, ex.getCode());
    }
}
