package cn.iocoder.yudao.module.mate.service.moment;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.module.mate.dal.dataobject.auth.SysUserDO;
import cn.iocoder.yudao.module.mate.dal.dataobject.moment.HomepageConfigDO;
import cn.iocoder.yudao.module.mate.dal.mysql.moment.HomepageConfigMapper;
import cn.iocoder.yudao.module.mate.dal.mysql.moment.UserFollowMapper;
import cn.iocoder.yudao.module.mate.service.auth.AuthService;
import cn.iocoder.yudao.module.mate.service.goods.GoodsService;
import jakarta.annotation.Resource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.mate.enums.ErrorCodeConstants.PARAM_INVALID;
import static cn.iocoder.yudao.module.mate.enums.ErrorCodeConstants.RESOURCE_OWNER_MISMATCH;

/**
 * M5 主页实现。
 * 配置是"配置驱动渲染"的基础版：没有配置时返回一份默认模块顺序，而不是让前端自己兜底，
 * 否则同一份数据在不同端会渲染出不同版式。
 */
@Service
@Validated
public class HomepageServiceImpl implements HomepageService {

    private static final int SIGNATURE_MAX = 100;
    private static final int COVER_KEY_MAX = 255;
    private static final int HOMEPAGE_POSTS = 12;
    private static final int HOMEPAGE_GOODS = 6;

    /** 无配置时的默认模块顺序：动态在前，二手在后 */
    private static final Map<String, Object> DEFAULT_MODULES = Map.of(
            "modules", List.of("posts", "goods"),
            "order", List.of("posts", "goods"));

    @Resource
    private HomepageConfigMapper homepageConfigMapper;
    @Resource
    private UserFollowMapper userFollowMapper;
    @Resource
    private MomentPostService momentPostService;
    @Resource
    private GoodsService goodsService;
    @Resource
    private AuthService authService;

    @Override
    public Homepage get(Long viewerId, Long userId) {
        SysUserDO user = userId == null ? null : authService.getUser(userId);
        if (user == null) {
            throw exception(RESOURCE_OWNER_MISMATCH);
        }
        boolean self = Objects.equals(viewerId, userId);
        // 别人只看得到已发布内容；自己能看到自己的待审内容（"审核中"需要有地方展示）
        List<MomentPostService.PostItem> posts = momentPostService.listByUser(viewerId, userId, !self, HOMEPAGE_POSTS);
        List<GoodsService.GoodsItem> goods = goodsService.listOnSaleBySeller(userId, HOMEPAGE_GOODS);

        boolean following = !self && viewerId != null
                && userFollowMapper.selectByFollowerAndFollowee(viewerId, userId) != null;
        return new Homepage(userId, user.getNickname(), user.getAvatarUrl(), user.getAuthLevel(), user.getSchoolId(),
                userFollowMapper.countFollowing(userId), userFollowMapper.countFollowers(userId), following,
                toView(homepageConfigMapper.selectByUserId(userId)), posts, goods);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ConfigView updateConfig(Long userId, UpdateCmd cmd) {
        validate(cmd);
        HomepageConfigDO existing = homepageConfigMapper.selectByUserId(userId);
        if (existing == null) {
            HomepageConfigDO created = HomepageConfigDO.builder()
                    .userId(userId)
                    .coverKey(cmd.coverKey())
                    .signature(cmd.signature())
                    .modulesJson(cmd.modulesJson() == null ? DEFAULT_MODULES : cmd.modulesJson())
                    .build();
            try {
                homepageConfigMapper.insert(created);
                return toView(created);
            } catch (DuplicateKeyException ignored) {
                // 并发首次配置：另一个请求刚好插入成功，退化成更新
                existing = homepageConfigMapper.selectByUserId(userId);
            }
        }
        HomepageConfigDO update = new HomepageConfigDO();
        update.setConfigId(existing.getConfigId());
        // null 视为"本次不改"，避免客户端只改签名时把封面清空
        if (cmd.coverKey() != null) {
            update.setCoverKey(cmd.coverKey());
        }
        if (cmd.signature() != null) {
            update.setSignature(cmd.signature());
        }
        if (cmd.modulesJson() != null) {
            update.setModulesJson(cmd.modulesJson());
        }
        homepageConfigMapper.updateById(update);
        return toView(homepageConfigMapper.selectByUserId(userId));
    }

    private void validate(UpdateCmd cmd) {
        if (cmd == null) {
            throw exception(PARAM_INVALID);
        }
        if (cmd.signature() != null && cmd.signature().length() > SIGNATURE_MAX) {
            throw exception(PARAM_INVALID);
        }
        if (cmd.coverKey() != null) {
            // 与动态附件同一口径：存 object_key，不存完整 URL
            if (StrUtil.isBlank(cmd.coverKey()) || cmd.coverKey().length() > COVER_KEY_MAX
                    || StrUtil.startWithIgnoreCase(cmd.coverKey(), "http://")
                    || StrUtil.startWithIgnoreCase(cmd.coverKey(), "https://")) {
                throw exception(PARAM_INVALID);
            }
        }
    }

    private ConfigView toView(HomepageConfigDO config) {
        if (config == null) {
            return new ConfigView(null, null, DEFAULT_MODULES);
        }
        return new ConfigView(config.getCoverKey(), config.getSignature(),
                config.getModulesJson() == null ? DEFAULT_MODULES : config.getModulesJson());
    }
}
