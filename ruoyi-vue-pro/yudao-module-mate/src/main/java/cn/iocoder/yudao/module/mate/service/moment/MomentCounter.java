package cn.iocoder.yudao.module.mate.service.moment;

import cn.iocoder.yudao.module.mate.dal.mysql.moment.AggCount;
import cn.iocoder.yudao.module.mate.dal.mysql.moment.MomentCollectMapper;
import cn.iocoder.yudao.module.mate.dal.mysql.moment.MomentCommentMapper;
import cn.iocoder.yudao.module.mate.dal.mysql.moment.MomentLikeMapper;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * M5 计数（点赞/评论）读取器。
 *
 * <p>为什么不落 like_count 列：契约（数据库文档 M5 段）没有计数列。凭空加列等于再造一份"事实"，
 * 一旦与关系表不一致就无法判断谁对。所以计数一律由关系表 COUNT 派生，只有一条事实来源。
 *
 * <p>为什么不写回 MySQL：没有列可写。这里用 Redis 做 30 秒短缓存扛热点读：
 * <ul>
 *   <li>命中缓存直接返回；缓存只在"未命中时"用 COUNT 结果初始化，因此不会因为过期期间
 *       有写入而长期偏差（写全部经过 {@link #onLikeChanged}，键在就 INCR/DECR，键不在下次读重新 COUNT）；</li>
 *   <li>批量场景（信息流）直接用一次 GROUP BY，不走缓存 —— 一条 SQL 比 N 次 Redis 更省。</li>
 * </ul>
 *
 * <p>Redis 不可用时全部降级为直查 DB：计数是展示型数据，不值得让它拖垮信息流。
 */
@Component
public class MomentCounter {

    private static final Logger log = LoggerFactory.getLogger(MomentCounter.class);

    private static final String LIKE_KEY = "mate:moment:like:";
    private static final Duration TTL = Duration.ofSeconds(30);

    @Resource
    private MomentLikeMapper momentLikeMapper;
    @Resource
    private MomentCommentMapper momentCommentMapper;
    @Resource
    private MomentCollectMapper momentCollectMapper;
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /** 单条点赞数（带 30s 缓存） */
    public long likeCount(Long postId) {
        String key = LIKE_KEY + postId;
        try {
            String cached = stringRedisTemplate.opsForValue().get(key);
            if (cached != null) {
                return Long.parseLong(cached);
            }
            long db = nullToZero(momentLikeMapper.countByPost(postId));
            stringRedisTemplate.opsForValue().set(key, String.valueOf(db), TTL);
            return db;
        } catch (RuntimeException ex) {
            log.warn("[likeCount] Redis 不可用，降级直查 DB, postId={}", postId, ex);
            return nullToZero(momentLikeMapper.countByPost(postId));
        }
    }

    /** 单条评论数（只算已通过） */
    public long commentCount(Long postId) {
        return nullToZero(momentCommentMapper.countApprovedByPost(postId));
    }

    /**
     * 点赞数变化时同步缓存。键不存在就不创建：此时缓存已过期，
     * 下次读会重新 COUNT（结果已包含本次写入），凭空 INCR 一个不存在的键会得到错误基数。
     */
    public void onLikeChanged(Long postId, long delta) {
        String key = LIKE_KEY + postId;
        try {
            if (!Boolean.TRUE.equals(stringRedisTemplate.hasKey(key))) {
                return;
            }
            Long value = stringRedisTemplate.opsForValue().increment(key, delta);
            if (value != null && value < 0) {
                stringRedisTemplate.opsForValue().set(key, "0", TTL);
            }
        } catch (RuntimeException ex) {
            log.warn("[onLikeChanged] Redis 不可用，跳过缓存更新, postId={}", postId, ex);
        }
    }

    /** 批量点赞数，一次 GROUP BY 取回 */
    public Map<Long, Long> likeCounts(Collection<Long> postIds) {
        if (postIds == null || postIds.isEmpty()) {
            return Map.of();
        }
        return toMap(momentLikeMapper.countGroupByPostIds(postIds));
    }

    /** 批量评论数 */
    public Map<Long, Long> commentCounts(Collection<Long> postIds) {
        if (postIds == null || postIds.isEmpty()) {
            return Map.of();
        }
        return toMap(momentCommentMapper.countGroupByPostIds(postIds));
    }

    /** 批量收藏数 */
    public Map<Long, Long> collectCounts(Collection<Long> postIds) {
        if (postIds == null || postIds.isEmpty()) {
            return Map.of();
        }
        return toMap(momentCollectMapper.countGroupByPostIds(postIds));
    }

    private Map<Long, Long> toMap(List<AggCount> rows) {
        Map<Long, Long> result = new HashMap<>();
        for (AggCount row : rows) {
            if (row.getId() != null) {
                result.put(row.getId(), nullToZero(row.getCnt()));
            }
        }
        return result;
    }

    private static long nullToZero(Long value) {
        return value == null ? 0L : value;
    }
}
