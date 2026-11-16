package cn.iocoder.yudao.module.mate.service.match;

import cn.iocoder.yudao.module.mate.dal.dataobject.match.MateMatchDO;

import java.util.List;

/**
 * M2 滑动匹配 + 附近的人 Service。
 */
public interface MatchService {

    /** 附近用户卡片 */
    record NearbyUser(Long userId, String nickname, Integer authLevel, Integer schoolId, Double distanceKm) {}

    /** 上报定位，写入 Redis GEO */
    void reportLocation(Long userId, Double longitude, Double latitude, String cityCode);

    /** 附近的人：GEO 半径查询 + 同校优先/认证加权排序 */
    List<NearbyUser> nearby(Long userId, String cityCode, Double longitude, Double latitude, Double radiusKm);

    /** 滑动：action=1喜欢2跳过。双向喜欢返回匹配关系，否则 null */
    MateMatchDO swipe(Long userId, Long targetId, Integer action);
}
