package cn.iocoder.yudao.module.mate.controller.v1.trade;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.mate.dal.dataobject.trade.ReconDiffDO;
import cn.iocoder.yudao.module.mate.dal.mysql.trade.ReconDiffMapper;
import cn.iocoder.yudao.module.mate.framework.web.CursorResult;
import cn.iocoder.yudao.module.mate.framework.web.InternalTokenGuard;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.annotation.security.PermitAll;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.module.mate.enums.ErrorCodeConstants.RESOURCE_OWNER_MISMATCH;

/**
 * 内部接口 #48：对账差错工单（GET 列表 + PATCH 处置），共享密钥头保护。
 */
@Tag(name = "内部 - 对账差错")
@RestController
@RequestMapping("/api/v1/admin/recon-diffs")
@Validated
public class InternalReconController {

    private static final int MAX_PAGE_SIZE = 100;

    @Resource
    private ReconDiffMapper reconDiffMapper;
    @Resource
    private InternalTokenGuard internalTokenGuard;

    public record ReconDiffVO(Long id, String diffNo, String bizNo, BigDecimal localAmount,
                              BigDecimal channelAmount, Integer type, Integer status) {
    }

    public record HandleRespVO(boolean updated) {
    }

    @GetMapping
    @Operation(summary = "对账差错列表（游标）")
    @PermitAll
    public CommonResult<CursorResult<ReconDiffVO>> list(
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false, defaultValue = "50") Integer size) {
        assertInternal(token);
        int pageSize = size <= 0 ? 50 : Math.min(size, MAX_PAGE_SIZE);
        Long beforeId = parseCursor(cursor);
        List<ReconDiffDO> rows = reconDiffMapper.selectListByStatus(status, beforeId, pageSize + 1);
        boolean hasMore = rows.size() > pageSize;
        if (hasMore) {
            rows = rows.subList(0, pageSize);
        }
        String nextCursor = hasMore ? String.valueOf(rows.get(rows.size() - 1).getId()) : null;
        List<ReconDiffVO> list = rows.stream()
                .map(d -> new ReconDiffVO(d.getId(), d.getDiffNo(), d.getBizNo(), d.getLocalAmount(),
                        d.getChannelAmount(), d.getType(), d.getStatus()))
                .toList();
        return success(new CursorResult<>(list, nextCursor, hasMore));
    }

    @PatchMapping("/{id}")
    @Operation(summary = "处置对账差错")
    @PermitAll
    public CommonResult<HandleRespVO> handle(
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @PathVariable("id") Long id) {
        assertInternal(token);
        ReconDiffDO diff = reconDiffMapper.selectById(id);
        if (diff == null) {
            throw exception(RESOURCE_OWNER_MISMATCH);
        }
        ReconDiffDO update = new ReconDiffDO();
        update.setId(id);
        update.setStatus(1);
        reconDiffMapper.updateById(update);
        return success(new HandleRespVO(true));
    }

    private void assertInternal(String token) {
        internalTokenGuard.check(token);
    }

    private Long parseCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(cursor);
        } catch (NumberFormatException ex) {
            throw exception(RESOURCE_OWNER_MISMATCH);
        }
    }
}
