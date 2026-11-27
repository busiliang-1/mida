package cn.iocoder.yudao.module.mate.dal.dataobject.moment;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.*;

import java.util.Map;

/**
 * M5 主页配置 DO：配置驱动渲染，前端按 modules_json 的顺序/开关决定渲染哪些模块。
 * user_id 唯一，所以更新用"先 update，0 行再 insert 并捕获并发重复键"。
 */
@TableName(value = "homepage_config", autoResultMap = true)
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HomepageConfigDO extends BaseDO {

    @TableId(type = IdType.ASSIGN_ID)
    private Long configId;
    private Long userId;
    /** 封面图对象键 */
    private String coverKey;
    private String signature;
    /** 模块列表/开关/顺序，落 MySQL JSON 列 */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> modulesJson;
}
