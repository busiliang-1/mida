package cn.iocoder.yudao.module.mate.service.moment;

import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.module.mate.dal.dataobject.auth.SysUserDO;
import cn.iocoder.yudao.module.mate.dal.dataobject.moment.MomentAttachmentDO;
import cn.iocoder.yudao.module.mate.dal.dataobject.moment.MomentCommentDO;
import cn.iocoder.yudao.module.mate.dal.dataobject.moment.MomentPostDO;
import cn.iocoder.yudao.module.mate.dal.mysql.moment.MomentAttachmentMapper;
import cn.iocoder.yudao.module.mate.dal.mysql.moment.MomentCollectMapper;
import cn.iocoder.yudao.module.mate.dal.mysql.moment.MomentCommentMapper;
import cn.iocoder.yudao.module.mate.dal.mysql.moment.MomentLikeMapper;
import cn.iocoder.yudao.module.mate.dal.mysql.moment.MomentPostMapper;
import cn.iocoder.yudao.module.mate.enums.moment.AuditStatusEnum;
import cn.iocoder.yudao.module.mate.enums.moment.PublishStatusEnum;
import cn.iocoder.yudao.module.mate.enums.moment.VisibleScopeEnum;
import cn.iocoder.yudao.module.mate.event.MomentSubmittedEvent;
import cn.iocoder.yudao.module.mate.framework.web.CursorPage;
import cn.iocoder.yudao.module.mate.service.auth.AuthService;
import cn.iocoder.yudao.module.mate.service.risk.SensitiveWordService;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.mate.enums.ErrorCodeConstants.AUTH_LEVEL_INSUFFICIENT;
import static cn.iocoder.yudao.module.mate.enums.ErrorCodeConstants.IDEMPOTENT_DUPLICATE;
import static cn.iocoder.yudao.module.mate.enums.ErrorCodeConstants.PARAM_INVALID;
import static cn.iocoder.yudao.module.mate.enums.ErrorCodeConstants.RESOURCE_OWNER_MISMATCH;

/**
 * M5 动态实现。
 *
 * <p>三个关键取舍：
 * <ol>
 *   <li><b>先审后发靠单字段</b>：待审期间 publish_status=0（仅自己可见），审核通过才置 1。
 *       信息流只查 publish_status=1，所以待审内容不可能泄漏到别人时间线，Service 层不必再兜。</li>
 *   <li><b>计数不落列</b>：契约表无 like_count，一律从关系表 COUNT 派生（见 {@link MomentCounter}），
 *       避免出现第二份"事实"。</li>
 *   <li><b>幂等不建表</b>：契约表没有幂等键列，用 Redis SETNX 记录"用户 + Idempotency-Key → 业务 ID"，
 *       TTL 24h；写入失败会释放占位，避免一次失败的发布把用户锁 24 小时。</li>
 * </ol>
 */
@Service
@Validated
public class MomentPostServiceImpl implements MomentPostService {

    private static final Logger log = LoggerFactory.getLogger(MomentPostServiceImpl.class);

    private static final int CONTENT_MAX = 2000;
    private static final int COMMENT_MAX = 500;
    private static final int IMAGES_MAX = 9;
    private static final int OBJECT_KEY_MAX = 255;
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 50;
    private static final int COMMENT_PAGE_SIZE = 20;

    private static final String IDEMPOTENT_PREFIX = "mate:moment:idem:";
    private static final String PENDING = "PENDING";
    private static final Duration IDEMPOTENT_TTL = Duration.ofHours(24);

    @Resource
    private MomentPostMapper momentPostMapper;
    @Resource
    private MomentAttachmentMapper momentAttachmentMapper;
    @Resource
    private MomentCommentMapper momentCommentMapper;
    @Resource
    private MomentLikeMapper momentLikeMapper;
    @Resource
    private MomentCollectMapper momentCollectMapper;
    @Resource
    private MomentCounter momentCounter;
    @Resource
    private AuthService authService;
    @Resource
    private SensitiveWordService sensitiveWordService;
    @Resource
    private ApplicationEventPublisher eventPublisher;
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    // ==================== #49 发动态 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PublishResult publish(Long userId, PublishCmd cmd, String idempotencyKey) {
        assertLevel(userId, 2);
        validatePublish(cmd);
        int auditStatus = auditVerdict(cmd.content());
        if (StrUtil.isBlank(idempotencyKey)) {
            throw exception(PARAM_INVALID);
        }

        String key = idempotentKey(userId, "post", idempotencyKey);
        Long existingId = acquireIdempotentSlot(key);
        if (existingId != null) {
            MomentPostDO existing = momentPostMapper.selectById(existingId);
            if (existing != null) {
                return new PublishResult(existing.getPostId(), existing.getAuditStatus());
            }
        }
        try {
            MomentPostDO post = MomentPostDO.builder()
                    .userId(userId)
                    .content(cmd.content())
                    .topicId(cmd.topicId())
                    .auditStatus(auditStatus)
                    .publishStatus(auditStatus == AuditStatusEnum.PASSED.getStatus()
                            ? PublishStatusEnum.PUBLISHED.getStatus()
                            : PublishStatusEnum.SELF_ONLY.getStatus())
                    .visibleScope(VisibleScopeEnum.PUBLIC.getStatus())
                    .build();
            momentPostMapper.insert(post);
            saveAttachments(post.getPostId(), cmd.images());
            markIdempotent(key, post.getPostId());
            eventPublisher.publishEvent(new MomentSubmittedEvent(post.getPostId(),
                    MomentSubmittedEvent.TYPE_POST, userId, post.getContent(),
                    post.getAuditStatus(), LocalDateTime.now()));
            return new PublishResult(post.getPostId(), post.getAuditStatus());
        } catch (RuntimeException ex) {
            releaseIdempotent(key);
            throw ex;
        }
    }

    // ==================== #50 动态详情 ====================

    @Override
    public Detail detail(Long viewerId, Long postId) {
        MomentPostDO post = requireVisible(viewerId, postId);
        PostItem item = toItems(viewerId, List.of(post)).get(0);
        CursorPage<CommentItem> comments = commentPage(viewerId, postId, null, COMMENT_PAGE_SIZE);
        return new Detail(item, comments.list(), comments.nextCursor(), comments.hasMore());
    }

    @Override
    public CursorPage<CommentItem> commentPage(Long viewerId, Long postId, String cursor, int size) {
        requireVisible(viewerId, postId);
        int pageSize = size <= 0 ? COMMENT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);
        Long beforeId = parseCursor(cursor);
        List<MomentCommentDO> rows = new ArrayList<>(
                momentCommentMapper.selectApproved(postId, viewerId, beforeId, pageSize + 1));
        boolean hasMore = rows.size() > pageSize;
        if (hasMore) {
            rows = new ArrayList<>(rows.subList(0, pageSize));
        }
        String nextCursor = hasMore ? String.valueOf(rows.get(rows.size() - 1).getCommentId()) : null;
        return new CursorPage<>(toComments(rows), nextCursor, hasMore);
    }

    // ==================== #51 信息流 ====================

    @Override
    public CursorPage<PostItem> feed(Long viewerId, String type, String cursor, int size) {
        int pageSize = size <= 0 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);
        Long beforeId = parseCursor(cursor);
        Integer schoolId = schoolOf(viewerId);

        String normalized = StrUtil.isBlank(type) ? "recommend" : type.trim().toLowerCase();
        List<MomentPostDO> rows;
        switch (normalized) {
            case "recommend" -> rows = momentPostMapper.selectFeed(viewerId, schoolId, beforeId, pageSize + 1);
            case "follow" -> rows = momentPostMapper.selectFollowFeed(viewerId, schoolId, beforeId, pageSize + 1);
            case "school" -> {
                // 没做 L3 就没有"本校"，返回空流而不是退化成推荐（否则用户会以为自己看到了同校内容）
                rows = schoolId == null ? List.of()
                        : momentPostMapper.selectSchoolFeed(schoolId, beforeId, pageSize + 1);
            }
            default -> throw exception(PARAM_INVALID);
        }

        boolean hasMore = rows.size() > pageSize;
        if (hasMore) {
            rows = new ArrayList<>(rows.subList(0, pageSize));
        }
        String nextCursor = hasMore ? String.valueOf(rows.get(rows.size() - 1).getPostId()) : null;
        return new CursorPage<>(toItems(viewerId, rows), nextCursor, hasMore);
    }

    // ==================== #53 评论 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CommentItem comment(Long userId, Long postId, String content, Long parentId, String idempotencyKey) {
        assertLevel(userId, 2);
        validateComment(content);
        requireVisible(userId, postId);
        assertParentValid(postId, parentId);
        int auditStatus = auditVerdict(content);
        if (StrUtil.isBlank(idempotencyKey)) {
            throw exception(PARAM_INVALID);
        }

        String key = idempotentKey(userId, "comment", idempotencyKey);
        Long existingId = acquireIdempotentSlot(key);
        if (existingId != null) {
            MomentCommentDO existing = momentCommentMapper.selectById(existingId);
            if (existing != null) {
                return toComments(List.of(existing)).get(0);
            }
        }
        try {
            MomentCommentDO comment = MomentCommentDO.builder()
                    .postId(postId)
                    .userId(userId)
                    .parentId(parentId)
                    .content(content)
                    .auditStatus(auditStatus)
                    .build();
            momentCommentMapper.insert(comment);
            markIdempotent(key, comment.getCommentId());
            eventPublisher.publishEvent(new MomentSubmittedEvent(comment.getCommentId(),
                    MomentSubmittedEvent.TYPE_COMMENT, userId, content,
                    comment.getAuditStatus(), LocalDateTime.now()));
            return toComments(List.of(comment)).get(0);
        } catch (RuntimeException ex) {
            releaseIdempotent(key);
            throw ex;
        }
    }

    // ==================== 可见性与查询 ====================

    @Override
    public void assertVisible(Long viewerId, Long postId) {
        requireVisible(viewerId, postId);
    }

    @Override
    public List<PostItem> listByUser(Long viewerId, Long userId, boolean onlyPublished, int limit) {
        int pageSize = limit <= 0 ? DEFAULT_PAGE_SIZE : Math.min(limit, MAX_PAGE_SIZE);
        List<MomentPostDO> rows = momentPostMapper.selectByUser(userId, onlyPublished, null, pageSize);
        return toItems(viewerId, rows);
    }

    /**
     * 可见性判定。四道闸口按"最便宜的先判"排序：
     * 作者本人 → 是否已发布 → 仅自己 → 同校。任何一道不过都返回 1004（对外不区分"不存在"和"看不到"，
     * 否则可以靠错误码差异探测私密动态是否存在）。
     */
    private MomentPostDO requireVisible(Long viewerId, Long postId) {
        MomentPostDO post = postId == null ? null : momentPostMapper.selectById(postId);
        if (post == null) {
            throw exception(RESOURCE_OWNER_MISMATCH);
        }
        if (Objects.equals(post.getUserId(), viewerId)) {
            return post;
        }
        if (!Objects.equals(post.getPublishStatus(), PublishStatusEnum.PUBLISHED.getStatus())) {
            throw exception(RESOURCE_OWNER_MISMATCH);
        }
        int scope = post.getVisibleScope() == null
                ? VisibleScopeEnum.PUBLIC.getStatus() : post.getVisibleScope();
        if (scope == VisibleScopeEnum.SELF.getStatus()) {
            throw exception(RESOURCE_OWNER_MISMATCH);
        }
        if (scope == VisibleScopeEnum.SAME_SCHOOL.getStatus()) {
            Integer viewerSchool = schoolOf(viewerId);
            Integer authorSchool = schoolOf(post.getUserId());
            if (viewerSchool == null || !viewerSchool.equals(authorSchool)) {
                throw exception(RESOURCE_OWNER_MISMATCH);
            }
        }
        return post;
    }

    // ==================== 组装 ====================

    private List<PostItem> toItems(Long viewerId, List<MomentPostDO> posts) {
        if (posts.isEmpty()) {
            return List.of();
        }
        List<Long> postIds = posts.stream().map(MomentPostDO::getPostId).toList();

        Map<Long, List<String>> imagesByPost = new HashMap<>();
        for (MomentAttachmentDO attachment : momentAttachmentMapper.selectByPostIds(postIds)) {
            imagesByPost.computeIfAbsent(attachment.getPostId(), k -> new ArrayList<>())
                    .add(attachment.getObjectKey());
        }
        Map<Long, Long> likeCounts = momentCounter.likeCounts(postIds);
        Map<Long, Long> commentCounts = momentCounter.commentCounts(postIds);
        Map<Long, Long> collectCounts = momentCounter.collectCounts(postIds);
        Set<Long> liked = viewerId == null ? Set.of()
                : new LinkedHashSet<>(momentLikeMapper.selectLikedPostIds(viewerId, postIds));
        Set<Long> collected = viewerId == null ? Set.of()
                : new LinkedHashSet<>(momentCollectMapper.selectCollectedPostIds(viewerId, postIds));

        Map<Long, SysUserDO> authors = new HashMap<>();
        List<PostItem> items = new ArrayList<>(posts.size());
        for (MomentPostDO post : posts) {
            SysUserDO author = authors.computeIfAbsent(post.getUserId(), authService::getUser);
            items.add(new PostItem(post.getPostId(), post.getUserId(),
                    author == null ? null : author.getNickname(),
                    author == null ? null : author.getAvatarUrl(),
                    post.getContent(), post.getTopicId(),
                    imagesByPost.getOrDefault(post.getPostId(), List.of()),
                    post.getAuditStatus(), post.getPublishStatus(), post.getVisibleScope(),
                    likeCounts.getOrDefault(post.getPostId(), 0L),
                    commentCounts.getOrDefault(post.getPostId(), 0L),
                    collectCounts.getOrDefault(post.getPostId(), 0L),
                    liked.contains(post.getPostId()),
                    collected.contains(post.getPostId()),
                    post.getCreateTime()));
        }
        return items;
    }

    private List<CommentItem> toComments(List<MomentCommentDO> rows) {
        Map<Long, SysUserDO> authors = new HashMap<>();
        List<CommentItem> items = new ArrayList<>(rows.size());
        for (MomentCommentDO row : rows) {
            SysUserDO author = authors.computeIfAbsent(row.getUserId(), authService::getUser);
            items.add(new CommentItem(row.getCommentId(), row.getUserId(),
                    author == null ? null : author.getNickname(),
                    row.getContent(), row.getParentId(), row.getAuditStatus(), row.getCreateTime()));
        }
        return items;
    }

    // ==================== 校验 ====================

    private void validatePublish(PublishCmd cmd) {
        if (cmd == null || StrUtil.isBlank(cmd.content()) || cmd.content().length() > CONTENT_MAX) {
            throw exception(PARAM_INVALID);
        }
        List<String> images = cmd.images();
        if (images == null) {
            return;
        }
        if (images.size() > IMAGES_MAX) {
            throw exception(PARAM_INVALID);
        }
        for (String objectKey : images) {
            // 契约要求存 object_key 而不是完整 URL：带 http(s) 前缀说明前端传了 URL，直接拒掉
            if (StrUtil.isBlank(objectKey) || objectKey.length() > OBJECT_KEY_MAX
                    || StrUtil.startWithIgnoreCase(objectKey, "http://")
                    || StrUtil.startWithIgnoreCase(objectKey, "https://")) {
                throw exception(PARAM_INVALID);
            }
        }
    }

    private void validateComment(String content) {
        if (StrUtil.isBlank(content) || content.length() > COMMENT_MAX) {
            throw exception(PARAM_INVALID);
        }
    }

    private void assertParentValid(Long postId, Long parentId) {
        if (parentId == null) {
            return;
        }
        MomentCommentDO parent = momentCommentMapper.selectById(parentId);
        // 只做两级：父必须属于同一条动态，且本身是顶层评论
        if (parent == null || !Objects.equals(parent.getPostId(), postId) || parent.getParentId() != null) {
            throw exception(PARAM_INVALID);
        }
    }

    /**
     * 机器初审（同步版）：命中敏感词转人工，未命中直接通过。
     *
     * 为什么不是"一律待审"：那样每一条干净内容都要人工点一次通过，信息流永远是空的；
     * 也不是"命中直接拒"：用户看不到内容为什么发不出去，且违规内容落不到库里，人工无从追溯。
     * 折中为"命中转人工"——内容落库、仅作者可见，等 #58 队列裁决。
     */
    private int auditVerdict(String text) {
        boolean hit = SensitiveWordService.AuditResult.HIT.equals(sensitiveWordService.match(text).verdict());
        return hit ? AuditStatusEnum.MANUAL.getStatus() : AuditStatusEnum.PASSED.getStatus();
    }

    private void assertLevel(Long userId, int required) {
        Integer level = authService.getAuthLevel(userId);
        if (level == null || level < required) {
            throw exception(AUTH_LEVEL_INSUFFICIENT);
        }
    }

    private void saveAttachments(Long postId, List<String> images) {
        if (images == null || images.isEmpty()) {
            return;
        }
        for (int i = 0; i < images.size(); i++) {
            momentAttachmentMapper.insert(MomentAttachmentDO.builder()
                    .postId(postId)
                    .objectKey(images.get(i))
                    .mediaType(1)
                    .sortNo(i)
                    .build());
        }
    }

    // ==================== 幂等 ====================

    private String idempotentKey(Long userId, String bizType, String idempotencyKey) {
        String digest = DigestUtil.sha256Hex(userId + ":" + bizType + ":" + idempotencyKey);
        return IDEMPOTENT_PREFIX + bizType + ":" + userId + ":" + digest.substring(0, 32);
    }

    /**
     * 抢占幂等位。返回非 null 表示"这个键之前已经成功过"，直接复用结果；
     * 返回 null 表示本次拿到执行权（已占位），必须继续执行并在成功后回填业务 ID。
     */
    private Long acquireIdempotentSlot(String key) {
        boolean acquired;
        try {
            Long existing = parseId(stringRedisTemplate.opsForValue().get(key));
            if (existing != null) {
                return existing;
            }
            acquired = Boolean.TRUE.equals(
                    stringRedisTemplate.opsForValue().setIfAbsent(key, PENDING, IDEMPOTENT_TTL));
            if (!acquired) {
                Long again = parseId(stringRedisTemplate.opsForValue().get(key));
                if (again != null) {
                    return again;
                }
            }
        } catch (RuntimeException ex) {
            // Redis 故障时放弃幂等保护：内容发布可用性优先于"绝对不重复"
            log.warn("[acquireIdempotentSlot] Redis 不可用，本次跳过幂等保护, key={}", key, ex);
            return null;
        }
        if (!acquired) {
            // 另一个请求正在处理同一个键，且还没回填业务 ID
            throw exception(IDEMPOTENT_DUPLICATE);
        }
        return null;
    }

    private void markIdempotent(String key, Long bizId) {
        try {
            stringRedisTemplate.opsForValue().set(key, String.valueOf(bizId), IDEMPOTENT_TTL);
        } catch (RuntimeException ex) {
            log.warn("[markIdempotent] Redis 不可用，幂等位未回填, key={}", key, ex);
        }
    }

    private void releaseIdempotent(String key) {
        try {
            stringRedisTemplate.delete(key);
        } catch (RuntimeException ex) {
            log.warn("[releaseIdempotent] Redis 不可用，幂等位未释放, key={}", key, ex);
        }
    }

    private Long parseId(String value) {
        if (StrUtil.isBlank(value) || PENDING.equals(value)) {
            return null;
        }
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException ex) {
            return null;
        }
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

    private Integer schoolOf(Long userId) {
        if (userId == null) {
            return null;
        }
        SysUserDO user = authService.getUser(userId);
        return user == null ? null : user.getSchoolId();
    }
}
