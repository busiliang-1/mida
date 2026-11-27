package cn.iocoder.yudao.module.mate.service.moment;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.module.mate.dal.dataobject.moment.MomentCommentDO;
import cn.iocoder.yudao.module.mate.dal.dataobject.moment.MomentPostDO;
import cn.iocoder.yudao.module.mate.dal.mysql.moment.AuditQueueRow;
import cn.iocoder.yudao.module.mate.dal.mysql.moment.MomentAuditMapper;
import cn.iocoder.yudao.module.mate.dal.mysql.moment.MomentCommentMapper;
import cn.iocoder.yudao.module.mate.dal.mysql.moment.MomentPostMapper;
import cn.iocoder.yudao.module.mate.enums.moment.AuditStatusEnum;
import cn.iocoder.yudao.module.mate.enums.moment.PublishStatusEnum;
import cn.iocoder.yudao.module.mate.framework.web.CursorPage;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.util.ArrayList;
import java.util.List;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.mate.enums.ErrorCodeConstants.PARAM_INVALID;
import static cn.iocoder.yudao.module.mate.enums.ErrorCodeConstants.RESOURCE_STATUS_INVALID;

/**
 * M5 审核实现。
 *
 * <p>处置用 CAS（WHERE audit_status = 读到的前驱状态）：两个审核员同时点"通过"和"驳回"时，
 * 只有先落库的那个生效，另一个影响 0 行即视为"已被他人处置"，不会出现后写覆盖先写。
 * 通过动作同时把 publish_status 置 1，所以"审核通过"和"对外可见"在同一个 UPDATE 里原子完成。
 */
@Service
@Validated
public class MomentAuditServiceImpl implements MomentAuditService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int DEFAULT_PAGE_SIZE = 50;

    private static final Integer ACTION_PASS = 1;
    private static final Integer ACTION_REJECT = 2;
    @Resource
    private MomentAuditMapper momentAuditMapper;
    @Resource
    private MomentPostMapper momentPostMapper;
    @Resource
    private MomentCommentMapper momentCommentMapper;

    @Override
    public CursorPage<AuditItem> queue(Integer status, String cursor, int size) {
        int pageSize = size <= 0 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);
        Long beforeId = parseCursor(cursor);
        List<AuditQueueRow> rows = new ArrayList<>(
                momentAuditMapper.selectQueue(status, beforeId, pageSize + 1));
        boolean hasMore = rows.size() > pageSize;
        if (hasMore) {
            rows = new ArrayList<>(rows.subList(0, pageSize));
        }
        String nextCursor = hasMore ? String.valueOf(rows.get(rows.size() - 1).getBizId()) : null;
        List<AuditItem> items = rows.stream()
                .map(r -> new AuditItem(r.getBizType(), r.getBizId(), r.getUserId(), r.getContent(),
                        r.getAuditStatus(), r.getCreateTime()))
                .toList();
        return new CursorPage<>(items, nextCursor, hasMore);
    }

    @Override
    public boolean dispose(String bizType, Long bizId, Integer action) {
        if (!ACTION_PASS.equals(action) && !ACTION_REJECT.equals(action)) {
            throw exception(PARAM_INVALID);
        }
        if (TYPE_POST.equals(bizType)) {
            return disposePost(bizId, action);
        }
        if (TYPE_COMMENT.equals(bizType)) {
            return disposeComment(bizId, action);
        }
        throw exception(PARAM_INVALID);
    }

    private boolean disposePost(Long postId, Integer action) {
        MomentPostDO post = postId == null ? null : momentPostMapper.selectById(postId);
        if (post == null) {
            throw exception(RESOURCE_STATUS_INVALID);
        }
        if (!AuditStatusEnum.isAuditable(post.getAuditStatus())) {
            // 已通过/已驳回：重复处置不算错误，但也不产生变更
            return false;
        }
        int toStatus = ACTION_PASS.equals(action)
                ? AuditStatusEnum.PASSED.getStatus() : AuditStatusEnum.REJECTED.getStatus();
        if (!AuditStatusEnum.canTransition(post.getAuditStatus(), toStatus)) {
            throw exception(RESOURCE_STATUS_INVALID);
        }
        int publishStatus = ACTION_PASS.equals(action)
                ? PublishStatusEnum.PUBLISHED.getStatus() : post.getPublishStatus();
        return momentPostMapper.advanceAudit(postId, post.getAuditStatus(), toStatus, publishStatus) > 0;
    }

    private boolean disposeComment(Long commentId, Integer action) {
        MomentCommentDO comment = commentId == null ? null : momentCommentMapper.selectById(commentId);
        if (comment == null) {
            throw exception(RESOURCE_STATUS_INVALID);
        }
        if (!AuditStatusEnum.isAuditable(comment.getAuditStatus())) {
            return false;
        }
        int toStatus = ACTION_PASS.equals(action)
                ? AuditStatusEnum.PASSED.getStatus() : AuditStatusEnum.REJECTED.getStatus();
        if (!AuditStatusEnum.canTransition(comment.getAuditStatus(), toStatus)) {
            throw exception(RESOURCE_STATUS_INVALID);
        }
        return momentCommentMapper.advanceAudit(commentId, comment.getAuditStatus(), toStatus) > 0;
    }

    private Long parseCursor(String cursor) {
        if (StrUtil.isBlank(cursor)) {
            return null;
        }
        try {
            return Long.valueOf(cursor);
        } catch (NumberFormatException ex) {
            throw exception(PARAM_INVALID);
        }
    }
}
