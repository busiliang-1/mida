package cn.iocoder.yudao.module.mate.service.match;

import cn.iocoder.yudao.module.mate.dal.dataobject.auth.SysUserDO;
import cn.iocoder.yudao.module.mate.dal.dataobject.match.MateLikeDO;
import cn.iocoder.yudao.module.mate.dal.dataobject.match.MateMatchDO;
import cn.iocoder.yudao.module.mate.dal.mysql.auth.SysUserMapper;
import cn.iocoder.yudao.module.mate.dal.mysql.match.MateLikeMapper;
import cn.iocoder.yudao.module.mate.dal.mysql.match.MateMatchMapper;
import cn.iocoder.yudao.module.mate.event.MatchCreatedEvent;
import jakarta.annotation.Resource;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.geo.Circle;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.geo.Metrics;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands.GeoLocation;
import org.springframework.data.redis.connection.RedisGeoCommands.GeoSearchCommandArgs;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.mate.enums.ErrorCodeConstants.MATCH_QUOTA_EXCEEDED;
import static cn.iocoder.yudao.module.mate.enums.ErrorCodeConstants.PARAM_INVALID;

@Service
@Validated
public class MatchServiceImpl implements MatchService {

    private static final int FREE_SWIPE_DAILY_LIMIT = 50;

    @Resource
    private MateLikeMapper likeMapper;
    @Resource
    private MateMatchMapper matchMapper;
    @Resource
    private SysUserMapper sysUserMapper;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private ApplicationEventPublisher eventPublisher;

    private String geoKey(String cityCode) {
        return "geo:online:" + cityCode;
    }

    @Override
    public void reportLocation(Long userId, Double longitude, Double latitude, String cityCode) {
        stringRedisTemplate.opsForGeo().add(geoKey(cityCode), new Point(longitude, latitude), userId.toString());
    }

    @Override
    public List<NearbyUser> nearby(Long userId, String cityCode, Double longitude, Double latitude, Double radiusKm) {
        SysUserDO me = sysUserMapper.selectById(userId);
        double radius = radiusKm == null ? 3.0 : radiusKm;
        GeoResults<GeoLocation<String>> results = stringRedisTemplate.opsForGeo().search(
                geoKey(cityCode),
                GeoReference.fromCoordinate(longitude, latitude),
                new Distance(radius, Metrics.KILOMETERS),
                GeoSearchCommandArgs.newGeoSearchArgs().includeDistance().sortAscending().limit(50));
        List<NearbyUser> list = new ArrayList<>();
        if (results == null) {
            return list;
        }
        for (var content : results.getContent()) {
            Long uid = Long.valueOf(content.getContent().getName());
            if (uid.equals(userId)) {
                continue;
            }
            SysUserDO u = sysUserMapper.selectById(uid);
            if (u == null) {
                continue;
            }
            double dist = content.getDistance().getValue();
            list.add(new NearbyUser(uid, u.getNickname(), u.getAuthLevel(), u.getSchoolId(), dist));
        }
        // 排序：同校优先(1.35) × (认证加权 + 距离衰减)
        Integer mySchool = me == null ? null : me.getSchoolId();
        list.sort(Comparator.comparingDouble((NearbyUser n) -> {
            double schoolCoef = (mySchool != null && mySchool.equals(n.schoolId())) ? 1.35 : 1.0;
            double authScore = (n.authLevel() == null ? 1 : n.authLevel()) / 3.0;
            double distDecay = 1.0 / (1.0 + n.distanceKm());
            return -(schoolCoef * (authScore + distDecay));
        }));
        return list;
    }

    @Override
    public MateMatchDO swipe(Long userId, Long targetId, Integer action) {
        if (userId.equals(targetId)) {
            throw exception(PARAM_INVALID);
        }
        // 每日滑动配额（免费用户）
        String quotaKey = "swipe:quota:" + userId + ":" + LocalDate.now();
        Long count = stringRedisTemplate.opsForValue().increment(quotaKey);
        if (count != null && count == 1L) {
            stringRedisTemplate.expire(quotaKey, 1, TimeUnit.DAYS);
        }
        if (count != null && count > FREE_SWIPE_DAILY_LIMIT) {
            throw exception(MATCH_QUOTA_EXCEEDED);
        }
        // 写入单向动作（唯一约束 liker+likee）
        MateLikeDO like = likeMapper.selectByLikerAndLikee(userId, targetId);
        if (like == null) {
            likeMapper.insert(MateLikeDO.builder().likerId(userId).likeeId(targetId).action(action).build());
        } else {
            MateLikeDO up = new MateLikeDO();
            up.setLikeId(like.getLikeId());
            up.setAction(action);
            likeMapper.updateById(up);
        }
        if (action != 1) {
            return null;
        }
        // 反查对方是否也喜欢自己 → 双向匹配
        MateLikeDO reverse = likeMapper.selectByLikerAndLikee(targetId, userId);
        if (reverse == null || reverse.getAction() != 1) {
            return null;
        }
        long low = Math.min(userId, targetId);
        long high = Math.max(userId, targetId);
        MateMatchDO existing = matchMapper.selectByLowHigh(low, high);
        if (existing != null) {
            return existing;
        }
        MateMatchDO match = MateMatchDO.builder()
                .userLowId(low).userHighId(high).source(1).greetingUsed(0)
                .build();
        matchMapper.insert(match);
        eventPublisher.publishEvent(new MatchCreatedEvent(low, high, 1));
        return match;
    }
}
