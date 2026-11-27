package cn.iocoder.yudao.module.mate.service.im;

import cn.iocoder.yudao.module.mate.framework.web.CursorPage;

import java.time.LocalDateTime;
import java.util.List;

/**
 * M3 会话 Service。
 */
public interface ImSessionService {

    /** 会话列表（含未读数），游标按最近活跃倒序 */
    CursorPage<SessionItem> listSessions(Long userId, String cursor, int size);

    /** 会话详情 + 成员，非成员返回 1004 */
    SessionDetail getSessionDetail(Long userId, Long sessionId);

    /** 匹配成功建单聊，幂等：同一 matchId 只建一个会话 */
    Long createSingleSession(Long userLowId, Long userHighId, Long matchId);

    /** 成团建群，幂等：同一 demandId 只建一个会话 */
    Long createGroupSession(Long demandId, Long ownerId, List<Long> memberIds);

    /** 非会话成员抛 1004 */
    void assertMember(Long userId, Long sessionId);

    List<Long> listMemberIds(Long sessionId);

    /** 已读位点只前进不后退 */
    void markRead(Long userId, Long sessionId, Long seq);

    record SessionItem(Long sessionId, Integer type, Long demandId, Long matchId, Integer memberCount,
                       Long maxSequence, Integer status, LocalDateTime lastActiveTime, Long unreadCount) {
    }

    record MemberItem(Long userId, Integer role, Long lastReadSeq) {
    }

    record SessionDetail(SessionItem session, List<MemberItem> members) {
    }
}
