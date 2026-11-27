package cn.iocoder.yudao.module.mate.service.im;

import java.util.Map;

/**
 * M3 卡片校验 SPI。
 * 为什么用 SPI 而不是 IM 里写 if(cardType==1)：卡片归属校验属于单据所属域（M2 需求 / M4 转账 / M7 二手），
 * IM 只负责"发卡片前必须校验通过"，各域注册自己的校验器，新增卡片类型不改 IM 代码。
 */
public interface CardValidator {

    /** 是否支持该卡片类型，见 ImCardTypeEnum */
    boolean supports(Integer cardType);

    /**
     * 校验单据存在、状态可用、且发送者对该单据有权限；不通过直接抛 1004。
     */
    void validate(Integer cardType, String bizNo, Long senderId);

    /**
     * 服务端实时渲染卡片内容；单据不存在时返回空态（{@code null}），不抛异常。
     */
    Map<String, Object> render(Integer cardType, String bizNo);
}
