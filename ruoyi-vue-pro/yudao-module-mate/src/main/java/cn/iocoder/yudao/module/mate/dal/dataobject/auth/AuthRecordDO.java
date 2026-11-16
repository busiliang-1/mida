package cn.iocoder.yudao.module.mate.dal.dataobject.auth;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 认证记录 DO。只存核验结论与材料摘要，不存证件影像/报告原文。
 */
@TableName("auth_record")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthRecordDO extends BaseDO {

    @TableId(type = IdType.ASSIGN_ID)
    private Long recordId;
    private Long userId;
    /** 认证类型 1实名2学校 */
    private Integer authType;
    /** 本次对应等级 */
    private Integer authLevel;
    /** 材料摘要 */
    private String materialDigest;
    /** 学籍报告编号，全局唯一 */
    private String reportNo;
    /** 核验渠道 MANUAL 人工 */
    private String verifyChannel;
    /** 核验结论 1通过2不通过3待人工 */
    private Integer verifyResult;
    /** 结论有效期 */
    private LocalDateTime expireAt;
    /** 人工审核操作人 */
    private String operator;
}
