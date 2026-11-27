package cn.iocoder.yudao.module.mate.controller.v1.im;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.mate.framework.auth.RequireAuthLevel;
import cn.iocoder.yudao.module.mate.framework.web.CursorPage;
import cn.iocoder.yudao.module.mate.framework.web.CursorResult;
import cn.iocoder.yudao.module.mate.service.im.ImMessageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.module.mate.enums.ErrorCodeConstants.UNAUTHORIZED;

/**
 * M3 消息接口 #27 / #28 / #29。
 */
@Tag(name = "用户端 - IM 消息")
@RestController
@RequestMapping("/api/v1/messages")
@Validated
public class AppImMessageController {

    @Resource
    private ImMessageService messageService;

    public record SendReqVO(@NotNull Long sessionId,
                            @NotNull Integer contentType,
                            @Size(max = 1000) String content,
                            Integer cardType,
                            @Size(max = 32) String bizNo,
                            @NotBlank @Size(max = 64) String clientMsgId) {
    }

    public record SendRespVO(Long messageId, Long sequence, Integer status) {
    }

    public record MessageVO(Long messageId, Long sessionId, Long seq, Long senderId, Integer contentType,
                            String content, Integer cardType, String bizNo, Integer status,
                            LocalDateTime createdTime) {
    }

    public record RecallRespVO(boolean recalled) {
    }

    @PostMapping
    @Operation(summary = "发送消息（文本/图片/卡片，落库成功才算成功）")
    @RequireAuthLevel(2)
    public CommonResult<SendRespVO> send(@Valid @RequestBody SendReqVO reqVO) {
        ImMessageService.SendResult result = messageService.send(requireLogin(),
                new ImMessageService.SendCmd(reqVO.sessionId(), reqVO.contentType(), reqVO.content(),
                        reqVO.cardType(), reqVO.bizNo(), reqVO.clientMsgId()));
        return success(new SendRespVO(result.messageId(), result.seq(), result.status()));
    }

    @GetMapping
    @Operation(summary = "历史消息 / 断线补齐（游标）")
    @RequireAuthLevel(2)
    public CommonResult<CursorResult<MessageVO>> history(@RequestParam Long sessionId,
                                                         @RequestParam(required = false) String cursor,
                                                         @RequestParam(required = false, defaultValue = "20") Integer size) {
        CursorPage<ImMessageService.MessageItem> page = messageService.history(requireLogin(), sessionId, cursor, size);
        List<MessageVO> list = page.list().stream()
                .map(m -> new MessageVO(m.messageId(), m.sessionId(), m.seq(), m.senderId(), m.contentType(),
                        m.content(), m.cardType(), m.bizNo(), m.status(), m.createdTime()))
                .toList();
        return success(new CursorResult<>(list, page.nextCursor(), page.hasMore()));
    }

    @PatchMapping("/{id}/recall")
    @Operation(summary = "撤回（≤2 分钟，仅本人）")
    @RequireAuthLevel(2)
    public CommonResult<RecallRespVO> recall(@PathVariable("id") Long id) {
        return success(new RecallRespVO(messageService.recall(requireLogin(), id)));
    }

    private Long requireLogin() {
        Long userId = SecurityFrameworkUtils.getLoginUserId();
        if (userId == null) {
            throw ServiceExceptionUtil.exception(UNAUTHORIZED);
        }
        return userId;
    }
}
