package cn.iocoder.yudao.module.mate.dal.dataobject.trade;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.time.LocalDateTime;

/**
 * M4 权益记录 DO。键值化设计：新增权益只加数据，不改表结构、不改业务代码。
 * uk(user_id, benefit_key, source_order_no) 保证同一订单重复回调只发一次权益。
 */
@TableName("member_benefit")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MemberBenefitDO extends BaseDO {

    @TableId(type = IdType.ASSIGN_ID)
    private Long benefitId;
    private Long userId;
    private String benefitKey;
    private String benefitValue;
    private String sourceOrderNo;
    private LocalDateTime expireAt;
    /** 0生效1失效2回收 */
    private Integer status;
}
