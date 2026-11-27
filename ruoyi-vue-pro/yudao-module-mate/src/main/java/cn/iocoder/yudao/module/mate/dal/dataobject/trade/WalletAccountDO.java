package cn.iocoder.yudao.module.mate.dal.dataobject.trade;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.math.BigDecimal;

/**
 * M4 钱包账户 DO。一人一户；余额只能通过 Mapper 的 debit/credit 改动，业务层不得 setBalance。
 */
@TableName("wallet_account")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WalletAccountDO extends BaseDO {

    @TableId(type = IdType.ASSIGN_ID)
    private Long accountId;
    private Long userId;
    private BigDecimal availableAmount;
    private BigDecimal frozenAmount;
    /** 乐观锁版本号 */
    private Integer version;
}
