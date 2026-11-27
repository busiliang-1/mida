package cn.iocoder.yudao.module.mate.dal.dataobject.im;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

/**
 * M3 消息 DO。
 * 双唯一键保证幂等：uk_session_seq(session_id,seq)、uk_session_client(session_id,client_msg_id)。
 */
@TableName("im_message")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImMessageDO extends BaseDO {

    @TableId(type = IdType.ASSIGN_ID)
    private Long messageId;
    private Long sessionId;
    /** 会话内单调递增序列号 */
    private Long seq;
    private Long senderId;
    /** 1文本2图片3卡片 */
    private Integer contentType;
    private String content;
    /** 1需求2转账3二手 */
    private Integer cardType;
    /** 卡片单据编号，只带编号不带快照 */
    private String bizNo;
    /** 客户端去重键 */
    private String clientMsgId;
    /** 0发送中1已送达2失败3撤回 */
    private Integer status;
}
