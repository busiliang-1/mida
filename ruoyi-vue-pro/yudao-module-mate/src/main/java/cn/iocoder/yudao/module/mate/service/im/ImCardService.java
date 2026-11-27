package cn.iocoder.yudao.module.mate.service.im;

import java.util.Map;

/**
 * M3 卡片 Service：统一的校验入口与渲染入口。
 * 防伪关键点：消息里只存 cardType + bizNo，任何展示都以服务端实时查询结果为准，客户端无法伪造金额/标题。
 */
public interface ImCardService {

    /** 发送前校验，非法单据抛 1004 */
    void validateOrThrow(Integer cardType, String bizNo, Long senderId);

    /** 渲染卡片；查不到或类型暂未接入返回 null（前端展示空态） */
    Map<String, Object> render(Integer cardType, String bizNo);
}
