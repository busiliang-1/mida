package cn.iocoder.yudao.module.mate.dal.dataobject.goods;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.*;

import java.math.BigDecimal;
import java.util.List;

/**
 * M7 二手商品 DO。version 是下单并发控制的关键：LOCK 必须带 version，避免两人同时抢到。
 */
@TableName(value = "goods_item", autoResultMap = true)
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GoodsItemDO extends BaseDO {

    @TableId(type = IdType.ASSIGN_ID)
    private Long goodsId;
    private Long sellerId;
    private String title;
    private String category;
    private BigDecimal price;
    private String description;
    /** 图片 object_key 列表，落 MySQL JSON 列 */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> images;
    /** 0在售1已锁定2已完成3已下架 */
    private Integer status;
    /** 乐观锁，防重复下单 */
    private Integer version;
}
