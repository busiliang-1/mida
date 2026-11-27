package cn.iocoder.yudao.module.mate.dal.dataobject.trade;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * M4 支付回调记录 DO。event_id 唯一：回调去重第一道，先落库再处理业务。
 */
@TableName("trade_pay_notify")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TradePayNotifyDO extends BaseDO {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String eventId;
    private String orderNo;
    private String tradeNo;
    private BigDecimal amount;
    private String status;
    private String rawBody;
    private LocalDateTime handledAt;
}
