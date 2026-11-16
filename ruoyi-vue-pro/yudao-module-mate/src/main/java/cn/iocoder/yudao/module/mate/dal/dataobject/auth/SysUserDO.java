package cn.iocoder.yudao.module.mate.dal.dataobject.auth;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

/**
 * 觅搭 C 端用户 DO（区别于底座 system_users 后台管理员）。
 */
@TableName("sys_user")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SysUserDO extends BaseDO {

    @TableId(type = IdType.ASSIGN_ID)
    private Long userId;
    /** 昵称 */
    private String nickname;
    /** 手机号 AES 密文 */
    private String phoneCipher;
    /** 手机号 HMAC-SHA256，用于等值查询 */
    private String phoneHash;
    /** 头像 */
    private String avatarUrl;
    /** 性别 0未知1男2女 */
    private Integer gender;
    /** 学校编号，L3 后写入 */
    private Integer schoolId;
    /** 年级 */
    private Integer grade;
    /** 认证等级 1-3 */
    private Integer authLevel;
    /** 信用分 */
    private Integer creditScore;
    /** 状态 0正常1限制2封禁 */
    private Integer status;
}
