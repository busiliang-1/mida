package cn.iocoder.yudao.module.mate.service.im;

import cn.iocoder.yudao.module.mate.dal.dataobject.im.ImBlacklistDO;
import cn.iocoder.yudao.module.mate.dal.mysql.im.ImBlacklistMapper;
import jakarta.annotation.Resource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.util.List;
import java.util.Objects;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.mate.enums.ErrorCodeConstants.PARAM_INVALID;

/**
 * M3 拉黑实现。
 * 并发重复拉黑会撞 uk_user_blocked，捕获 DuplicateKeyException 按幂等成功返回，不向调用方抛 500。
 */
@Service
@Validated
public class ImBlacklistServiceImpl implements ImBlacklistService {

    @Resource
    private ImBlacklistMapper blacklistMapper;

    @Override
    public boolean block(Long userId, Long blockedUserId) {
        if (blockedUserId == null || Objects.equals(userId, blockedUserId)) {
            throw exception(PARAM_INVALID);
        }
        if (blacklistMapper.existsBlock(userId, blockedUserId)) {
            return true;
        }
        try {
            blacklistMapper.insert(ImBlacklistDO.builder()
                    .userId(userId).blockedUserId(blockedUserId).build());
        } catch (DuplicateKeyException ignored) {
            // 并发重复拉黑：唯一键已保证只有一行，按幂等成功处理
        }
        return true;
    }

    @Override
    public boolean unblock(Long userId, Long blockedUserId) {
        if (blockedUserId == null) {
            throw exception(PARAM_INVALID);
        }
        blacklistMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ImBlacklistDO>()
                .eq(ImBlacklistDO::getUserId, userId)
                .eq(ImBlacklistDO::getBlockedUserId, blockedUserId));
        return true;
    }

    @Override
    public boolean isBlockedBetween(Long userId, Long otherUserId) {
        if (userId == null || otherUserId == null) {
            return false;
        }
        return blacklistMapper.existsEitherDirection(userId, otherUserId);
    }

    @Override
    public List<Long> listBlockedUserIds(Long userId) {
        return blacklistMapper.selectListByUser(userId).stream()
                .map(ImBlacklistDO::getBlockedUserId)
                .toList();
    }
}
