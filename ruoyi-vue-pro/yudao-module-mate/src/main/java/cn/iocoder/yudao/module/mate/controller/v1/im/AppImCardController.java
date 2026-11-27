package cn.iocoder.yudao.module.mate.controller.v1.im;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.mate.framework.auth.RequireAuthLevel;
import cn.iocoder.yudao.module.mate.service.im.ImCardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/**
 * M3 卡片详情 #30。
 * 只按 cardType + bizNo 实时查询单据并服务端渲染，查不到返回 null（前端空态），不返回 500。
 */
@Tag(name = "用户端 - IM 卡片")
@RestController
@RequestMapping("/api/v1/cards")
@Validated
public class AppImCardController {

    @Resource
    private ImCardService cardService;

    public record CardRespVO(Map<String, Object> card) {
    }

    @GetMapping("/{cardType}/{bizNo}")
    @Operation(summary = "卡片详情（服务端渲染，查不到空态）")
    @RequireAuthLevel(2)
    public CommonResult<CardRespVO> getCard(@PathVariable("cardType") Integer cardType,
                                            @PathVariable("bizNo") String bizNo) {
        return success(new CardRespVO(cardService.render(cardType, bizNo)));
    }
}
