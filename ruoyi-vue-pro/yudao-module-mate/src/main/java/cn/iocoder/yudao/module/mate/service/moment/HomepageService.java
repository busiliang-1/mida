package cn.iocoder.yudao.module.mate.service.moment;

import cn.iocoder.yudao.module.mate.service.goods.GoodsService;

import java.util.List;
import java.util.Map;

/**
 * M5 主页 Service（#56 主页数据 / #57 主页配置）。
 *
 * <p>契约只要求 config / posts / goods 三个字段；这里额外带上用户身份、关注数和 viewer 的关注状态，
 * 因为"主页只给三块内容不给是谁的主页"前端无法渲染。只在响应里做加法，不改既有字段语义。
 */
public interface HomepageService {

    record ConfigView(String coverKey, String signature, Map<String, Object> modulesJson) {
    }

    record UpdateCmd(String coverKey, String signature, Map<String, Object> modulesJson) {
    }

    record Homepage(Long userId, String nickname, String avatarUrl, Integer authLevel, Integer schoolId,
                    Long followingCount, Long followerCount, boolean following,
                    ConfigView config, List<MomentPostService.PostItem> posts,
                    List<GoodsService.GoodsItem> goods) {
    }

    /** 主页数据；用户不存在返回 1004 */
    Homepage get(Long viewerId, Long userId);

    /**
     * 更新主页配置。三个字段都可选，语义为"给了就改，没给就保持不变"
     * （客户端常只改签名，用 PUT 全量替换会把封面清空，那不是用户意图）。
     */
    ConfigView updateConfig(Long userId, UpdateCmd cmd);
}
