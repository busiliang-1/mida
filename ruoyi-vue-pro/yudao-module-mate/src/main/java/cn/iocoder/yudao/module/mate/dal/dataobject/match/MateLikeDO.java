package cn.iocoder.yudao.module.mate.dal.dataobject.match;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

@TableName("mate_like")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MateLikeDO extends BaseDO {

    @TableId(type = IdType.ASSIGN_ID)
    private Long likeId;
    private Long likerId;
    private Long likeeId;
    /** 1喜欢2跳过 */
    private Integer action;
}
