package cn.iocoder.yudao.module.mate.controller.v1.moment;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.mate.framework.web.CursorResult;
import cn.iocoder.yudao.module.mate.framework.web.InternalTokenGuard;
import cn.iocoder.yudao.module.mate.service.moment.MomentAuditService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.annotation.security.PermitAll;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/**
 * 内部接口 #58：内容审核队列（GET）+ 处置（PUT）。
 * 与 #48 同一套共享密钥头鉴权：审核台是内部服务，不走用户令牌，也不该暴露在 C 端网关后面。
 */
@Tag(name = "内部 - 内容审核")
@RestController
@RequestMapping("/api/v1/admin/audit-queues")
@Validated
public class InternalAuditController {

    @Resource
    private MomentAuditService momentAuditService;
    @Resource
    private InternalTokenGuard internalTokenGuard;

    public record AuditItemVO(String bizType, Long bizId, Long userId, String content,
                              Integer auditStatus, LocalDateTime createTime) {
    }

    public record DisposeReqVO(@NotNull @Min(1) @Max(2) Integer action) {
    }

    public record DisposeRespVO(Boolean updated) {
    }

    @GetMapping
    @Operation(summary = "内容审核队列（待审 + 转人工）")
    @PermitAll
    public CommonResult<CursorResult<AuditItemVO>> queue(
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false, defaultValue = "50") Integer size) {
        assertInternal(token);
        var page = momentAuditService.queue(status, cursor, size);
        List<AuditItemVO> list = page.list().stream()
                .map(i -> new AuditItemVO(i.bizType(), i.bizId(), i.userId(), i.content(),
                        i.auditStatus(), i.createTime()))
                .toList();
        return success(new CursorResult<>(list, page.nextCursor(), page.hasMore()));
    }

    @PutMapping("/{id}")
    @Operation(summary = "处置内容：action=1 通过 / 2 驳回")
    @PermitAll
    public CommonResult<DisposeRespVO> dispose(
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @PathVariable("id") Long id,
            @RequestParam(required = false, defaultValue = "post") String type,
            @Valid @RequestBody DisposeReqVO reqVO) {
        assertInternal(token);
        return success(new DisposeRespVO(momentAuditService.dispose(type, id, reqVO.action())));
    }

    private void assertInternal(String token) {
        internalTokenGuard.check(token);
    }
}
