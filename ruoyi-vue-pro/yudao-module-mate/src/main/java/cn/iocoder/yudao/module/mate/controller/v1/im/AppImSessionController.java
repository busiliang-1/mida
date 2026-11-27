package cn.iocoder.yudao.module.mate.controller.v1.im;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.mate.framework.auth.RequireAuthLevel;
import cn.iocoder.yudao.module.mate.framework.web.CursorPage;
import cn.iocoder.yudao.module.mate.framework.web.CursorResult;
import cn.iocoder.yudao.module.mate.service.im.ImSessionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.module.mate.enums.ErrorCodeConstants.UNAUTHORIZED;

/**
 * M3 会话接口 #25 / #26。
 * 包路径 controller.v1 → 不匹配芋道 **.controller.app.** 规则，因此不加 /app-api，路径与接口文档一致。
 */
@Tag(name = "用户端 - IM 会话")
@RestController
@RequestMapping("/api/v1/sessions")
@Validated
public class AppImSessionController {

    @Resource
    private ImSessionService sessionService;

    public record SessionVO(Long sessionId, Integer type, Long demandId, Long matchId, Integer memberCount,
                            Long maxSequence, Integer status, LocalDateTime lastActiveTime, Long unreadCount) {
    }

    public record MemberVO(Long userId, Integer role, Long lastReadSeq) {
    }

    public record SessionDetailVO(SessionVO session, List<MemberVO> members) {
    }

    @GetMapping
    @Operation(summary = "会话列表（含未读数）")
    public CommonResult<CursorResult<SessionVO>> listSessions(
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false, defaultValue = "20") Integer size) {
        Long userId = requireLogin();
        CursorPage<ImSessionService.SessionItem> page = sessionService.listSessions(userId, cursor, size);
        List<SessionVO> list = page.list().stream().map(AppImSessionController::toVO).toList();
        return success(new CursorResult<>(list, page.nextCursor(), page.hasMore()));
    }

    @GetMapping("/{id}")
    @Operation(summary = "会话详情 + 成员")
    @RequireAuthLevel(2)
    public CommonResult<SessionDetailVO> getSession(@PathVariable("id") Long id) {
        ImSessionService.SessionDetail detail = sessionService.getSessionDetail(requireLogin(), id);
        List<MemberVO> members = detail.members().stream()
                .map(m -> new MemberVO(m.userId(), m.role(), m.lastReadSeq()))
                .toList();
        return success(new SessionDetailVO(toVO(detail.session()), members));
    }

    /** #25 的未登录错误码是 1002，等级切面会返回 1003，所以这里显式判登录 */
    private Long requireLogin() {
        Long userId = SecurityFrameworkUtils.getLoginUserId();
        if (userId == null) {
            throw exception(UNAUTHORIZED);
        }
        return userId;
    }

    private static SessionVO toVO(ImSessionService.SessionItem item) {
        return new SessionVO(item.sessionId(), item.type(), item.demandId(), item.matchId(),
                item.memberCount(), item.maxSequence(), item.status(), item.lastActiveTime(), item.unreadCount());
    }
}
