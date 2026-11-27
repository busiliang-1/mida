package cn.iocoder.yudao.module.mate.service.goods;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.mate.dal.dataobject.goods.GoodsItemDO;
import cn.iocoder.yudao.module.mate.dal.dataobject.goods.GoodsOrderDO;
import cn.iocoder.yudao.module.mate.dal.mysql.goods.GoodsItemMapper;
import cn.iocoder.yudao.module.mate.dal.mysql.goods.GoodsOrderMapper;
import cn.iocoder.yudao.module.mate.enums.goods.GoodsOrderStatusEnum;
import cn.iocoder.yudao.module.mate.enums.goods.GoodsStatusEnum;
import cn.iocoder.yudao.module.mate.event.GoodsSoldEvent;
import cn.iocoder.yudao.module.mate.service.auth.AuthService;
import cn.iocoder.yudao.module.mate.service.risk.SensitiveWordService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * M7 关键约束单测：未 L3 挂售 1003、买自己 1004、重复下单 2002、乐观锁抢不到 2002、非法跃迁 2002。
 */
@ExtendWith(MockitoExtension.class)
class GoodsServiceImplTest {

    private static final Long SELLER = 21L;
    private static final Long BUYER = 22L;
    private static final Long GOODS_ID = 7001L;
    private static final Long ORDER_ID = 8001L;

    @Mock
    private GoodsItemMapper goodsItemMapper;
    @Mock
    private GoodsOrderMapper goodsOrderMapper;
    @Mock
    private AuthService authService;
    @Mock
    private SensitiveWordService sensitiveWordService;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private GoodsServiceImpl goodsService;

    private GoodsItemDO goods(int status, int version) {
        return GoodsItemDO.builder().goodsId(GOODS_ID).sellerId(SELLER).title("二手自行车")
                .category("其他").price(new BigDecimal("120.00")).images(List.of("k1.jpg"))
                .status(status).version(version).build();
    }

    @Test
    void publishRequiresL3() {
        when(authService.getAuthLevel(SELLER)).thenReturn(2);

        ServiceException ex = assertThrows(ServiceException.class, () -> goodsService.publish(SELLER,
                new GoodsService.PublishCmd("二手自行车", "其他", new BigDecimal("120.00"), null, List.of("k1.jpg"))));

        assertEquals(1003, ex.getCode());
        verify(goodsItemMapper, never()).insert(any(GoodsItemDO.class));
    }

    @Test
    void cannotBuyOwnGoods() {
        when(authService.getAuthLevel(SELLER)).thenReturn(3);
        when(goodsItemMapper.selectById(GOODS_ID)).thenReturn(goods(0, 0));

        ServiceException ex = assertThrows(ServiceException.class,
                () -> goodsService.createOrder(SELLER, GOODS_ID, "key-1"));

        assertEquals(1004, ex.getCode());
        verify(goodsItemMapper, never()).lock(anyLong(), any());
    }

    @Test
    void duplicateActiveOrderRejected() {
        when(authService.getAuthLevel(BUYER)).thenReturn(2);
        when(goodsItemMapper.selectById(GOODS_ID)).thenReturn(goods(0, 0));
        when(goodsOrderMapper.selectByNo(anyString())).thenReturn(null);
        when(goodsOrderMapper.countActiveByBuyer(BUYER)).thenReturn(1L);

        ServiceException ex = assertThrows(ServiceException.class,
                () -> goodsService.createOrder(BUYER, GOODS_ID, "key-2"));

        assertEquals(2002, ex.getCode());
    }

    @Test
    void optimisticLockFailureMeansSoldOut() {
        when(authService.getAuthLevel(BUYER)).thenReturn(2);
        when(goodsItemMapper.selectById(GOODS_ID)).thenReturn(goods(0, 3));
        when(goodsOrderMapper.selectByNo(anyString())).thenReturn(null);
        when(goodsOrderMapper.countActiveByBuyer(BUYER)).thenReturn(0L);
        when(goodsItemMapper.lock(GOODS_ID, 3)).thenReturn(0); // 别人先改过 version

        ServiceException ex = assertThrows(ServiceException.class,
                () -> goodsService.createOrder(BUYER, GOODS_ID, "key-3"));

        assertEquals(2002, ex.getCode());
        verify(goodsOrderMapper, never()).insert(any(GoodsOrderDO.class));
    }

    @Test
    void createOrderSuccess() {
        when(authService.getAuthLevel(BUYER)).thenReturn(2);
        when(goodsItemMapper.selectById(GOODS_ID)).thenReturn(goods(0, 0));
        when(goodsOrderMapper.selectByNo(anyString())).thenReturn(null);
        when(goodsOrderMapper.countActiveByBuyer(BUYER)).thenReturn(0L);
        when(goodsItemMapper.lock(GOODS_ID, 0)).thenReturn(1);
        lenient().when(goodsOrderMapper.insert(any(GoodsOrderDO.class))).thenReturn(1);

        GoodsService.OrderResult result = goodsService.createOrder(BUYER, GOODS_ID, "key-4");

        assertEquals(GoodsOrderStatusEnum.CREATED.getStatus(), result.status());
        assertEquals(true, result.orderNo().startsWith("G"));
    }

    @Test
    void confirmCancelledOrderRejected() {
        GoodsOrderDO cancelled = GoodsOrderDO.builder().orderId(ORDER_ID).orderNo("G-1").goodsId(GOODS_ID)
                .buyerId(BUYER).sellerId(SELLER).amount(new BigDecimal("120.00"))
                .status(GoodsOrderStatusEnum.CANCELLED.getStatus()).build();
        when(goodsOrderMapper.selectById(ORDER_ID)).thenReturn(cancelled);

        ServiceException ex = assertThrows(ServiceException.class,
                () -> goodsService.confirm(BUYER, ORDER_ID));

        assertEquals(2002, ex.getCode());
    }

    @Test
    void confirmCompletesGoodsAndPublishesEvent() {
        GoodsOrderDO created = GoodsOrderDO.builder().orderId(ORDER_ID).orderNo("G-2").goodsId(GOODS_ID)
                .buyerId(BUYER).sellerId(SELLER).amount(new BigDecimal("120.00"))
                .status(GoodsOrderStatusEnum.CREATED.getStatus()).build();
        when(goodsOrderMapper.selectById(ORDER_ID)).thenReturn(created);
        when(goodsOrderMapper.complete(ORDER_ID)).thenReturn(1);
        when(goodsItemMapper.markSold(GOODS_ID)).thenReturn(1);

        assertEquals(GoodsOrderStatusEnum.COMPLETED.getStatus(), goodsService.confirm(BUYER, ORDER_ID));
        verify(eventPublisher).publishEvent(any(GoodsSoldEvent.class));
    }

    @Test
    void statusMachineRejectsIllegalTransition() {
        // 已下架不能直接变已完成；已取消订单不能变已完成
        assertEquals(false, GoodsStatusEnum.canTransition(GoodsStatusEnum.OFF.getStatus(),
                GoodsStatusEnum.SOLD.getStatus()));
        assertEquals(true, GoodsStatusEnum.canTransition(GoodsStatusEnum.ON_SALE.getStatus(),
                GoodsStatusEnum.LOCKED.getStatus()));
        assertEquals(false, GoodsOrderStatusEnum.canTransition(GoodsOrderStatusEnum.CANCELLED.getStatus(),
                GoodsOrderStatusEnum.COMPLETED.getStatus()));
    }
}
