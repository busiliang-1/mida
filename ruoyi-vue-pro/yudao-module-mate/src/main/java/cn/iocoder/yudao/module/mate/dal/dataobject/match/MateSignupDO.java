package cn.iocoder.yudao.module.mate.dal.dataobject.match;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

@TableName("mate_signup")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MateSignupDO extends BaseDO {

    @TableId(type = IdType.ASSIGN_ID)
    private Long signupId;
    private Long demandId;
    private Long userId;
    private String message;
    /** 0待审1通过2拒绝3退出4关闭 */
    private Integer auditStatus;
    private Integer creditSnapshot;
}
