package cn.iocoder.yudao.module.mate.service.im;

import java.util.List;

/**
 * M3 拉黑 Service。语义为"双向阻断"：任一方拉黑，双方会话内都不能再发消息。
 */
public interface ImBlacklistService {

    /** 拉黑，重复拉黑按幂等成功返回 */
    boolean block(Long userId, Long blockedUserId);

    /** 解除拉黑，未拉黑也返回成功（幂等） */
    boolean unblock(Long userId, Long blockedUserId);

    /** 双向判定 */
    boolean isBlockedBetween(Long userId, Long otherUserId);

    /** 我拉黑的用户列表 */
    List<Long> listBlockedUserIds(Long userId);
}
