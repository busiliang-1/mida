package cn.iocoder.yudao.module.mate.dal.dataobject.trade;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * M4 会员/增值订单 DO。order_no 唯一：支付回调的幂等键。
 */
@TableName("member_order")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MemberOrderDO extends BaseDO {

    @TableId(type = IdType.ASSIGN_ID)
    private Long orderId;
    private String orderNo;
    private Long userId;
    /** 1会员2增值 */
    private Integer orderType;
    private Integer packageId;
    private Integer durationMonth;
    private BigDecimal amount;
    private BigDecimal paidAmount;
    private String payChannel;
    private String payNo;
    /** 0待支付1支付中2已生效3已退款4已关闭5已失效 */
    private Integer status;
    private LocalDateTime expireAt;
    private LocalDateTime paidAt;
}
