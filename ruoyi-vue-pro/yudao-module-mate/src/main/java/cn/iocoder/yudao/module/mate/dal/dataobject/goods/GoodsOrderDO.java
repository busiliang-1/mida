package cn.iocoder.yudao.module.mate.dal.dataobject.goods;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * M7 二手订单 DO。无资金托管：只记录成交事实，付款在线下完成。
 */
@TableName("goods_order")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GoodsOrderDO extends BaseDO {

    @TableId(type = IdType.ASSIGN_ID)
    private Long orderId;
    private String orderNo;
    private Long goodsId;
    private Long buyerId;
    private Long sellerId;
    private BigDecimal amount;
    /** 0待确认1已完成2已取消 */
    private Integer status;
    private LocalDateTime finishedAt;
}
