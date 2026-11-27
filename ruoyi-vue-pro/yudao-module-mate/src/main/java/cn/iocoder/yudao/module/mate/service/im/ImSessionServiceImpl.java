package cn.iocoder.yudao.module.mate.service.im;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.module.mate.dal.dataobject.im.ImSessionDO;
import cn.iocoder.yudao.module.mate.dal.dataobject.im.ImSessionMemberDO;
import cn.iocoder.yudao.module.mate.dal.mysql.im.ImMessageMapper;
import cn.iocoder.yudao.module.mate.dal.mysql.im.ImSessionMapper;
import cn.iocoder.yudao.module.mate.dal.mysql.im.ImSessionMemberMapper;
import cn.iocoder.yudao.module.mate.enums.im.ImSessionTypeEnum;
import cn.iocoder.yudao.module.mate.framework.web.CursorPage;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.mate.enums.ErrorCodeConstants.PARAM_INVALID;
import static cn.iocoder.yudao.module.mate.enums.ErrorCodeConstants.RESOURCE_OWNER_MISMATCH;

/**
 * M3 会话实现。
 * 会话列表用 (update_time, session_id) 组合游标，避免同一毫秒多条时漏数据；
 * 未读数按 im_message 精确统计（seq > last_read_seq 且非自己发送），不依赖 Redis 近似。
 */
@Service
@Validated
public class ImSessionServiceImpl implements ImSessionService {

    private static final int MAX_PAGE_SIZE = 50;
    private static final String CURSOR_SEPARATOR = "_";

    @Resource
    private ImSessionMapper sessionMapper;
    @Resource
    private ImSessionMemberMapper memberMapper;
    @Resource
    private ImMessageMapper messageMapper;

    @Override
    public CursorPage<SessionItem> listSessions(Long userId, String cursor, int size) {
        int pageSize = normalizeSize(size);
        List<ImSessionMemberDO> memberships = memberMapper.selectListByUser(userId);
        if (memberships.isEmpty()) {
            return CursorPage.empty();
        }
        long[] cursorPair = parseCursor(cursor);
        List<SessionItem> items = new ArrayList<>();
        for (ImSessionMemberDO membership : memberships) {
            ImSessionDO session = sessionMapper.selectById(membership.getSessionId());
            if (session == null || Objects.equals(session.getStatus(), 2)) {
                continue;
            }
            // 游标过滤：严格小于 (updateTime, sessionId)，保证不重不漏
            long activeMillis = toMillis(session.getUpdateTime());
            if (cursorPair != null) {
                boolean afterCursor = activeMillis < cursorPair[0]
                        || (activeMillis == cursorPair[0] && session.getSessionId() < cursorPair[1]);
                if (!afterCursor) {
                    continue;
                }
            }
            items.add(toItem(session, membership));
        }
        items.sort(Comparator.comparing(SessionItem::lastActiveTime,
                        Comparator.nullsLast(Comparator.naturalOrder())).reversed()
                .thenComparing(SessionItem::sessionId, Comparator.reverseOrder()));
        boolean hasMore = items.size() > pageSize;
        List<SessionItem> page = hasMore ? new ArrayList<>(items.subList(0, pageSize)) : items;
        String nextCursor = hasMore ? buildCursor(page.get(page.size() - 1)) : null;
        return new CursorPage<>(page, nextCursor, hasMore);
    }

    @Override
    public SessionDetail getSessionDetail(Long userId, Long sessionId) {
        assertMember(userId, sessionId);
        ImSessionDO session = sessionMapper.selectById(sessionId);
        if (session == null) {
            throw exception(RESOURCE_OWNER_MISMATCH);
        }
        ImSessionMemberDO me = memberMapper.selectBySessionAndUser(sessionId, userId);
        List<MemberItem> members = memberMapper.selectListBySession(sessionId).stream()
                .map(m -> new MemberItem(m.getUserId(), m.getRole(), m.getLastReadSeq()))
                .toList();
        return new SessionDetail(toItem(session, me), members);
    }

    @Override
    public Long createSingleSession(Long userLowId, Long userHighId, Long matchId) {
        ImSessionDO existing = sessionMapper.selectByMatchId(matchId);
        if (existing != null) {
            return existing.getSessionId();
        }
        ImSessionDO session = ImSessionDO.builder()
                .type(ImSessionTypeEnum.SINGLE.getType())
                .matchId(matchId)
                .memberCount(2)
                .maxSequence(0L)
                .status(0)
                .build();
        sessionMapper.insert(session);
        insertMember(session.getSessionId(), userLowId, 0);
        insertMember(session.getSessionId(), userHighId, 0);
        return session.getSessionId();
    }

    @Override
    public Long createGroupSession(Long demandId, Long ownerId, List<Long> memberIds) {
        ImSessionDO existing = sessionMapper.selectByDemandId(demandId);
        if (existing != null) {
            return existing.getSessionId();
        }
        List<Long> members = new ArrayList<>();
        members.add(ownerId);
        for (Long memberId : memberIds) {
            if (memberId != null && !members.contains(memberId)) {
                members.add(memberId);
            }
        }
        ImSessionDO session = ImSessionDO.builder()
                .type(ImSessionTypeEnum.GROUP.getType())
                .demandId(demandId)
                .memberCount(members.size())
                .maxSequence(0L)
                .status(0)
                .build();
        sessionMapper.insert(session);
        for (Long memberId : members) {
            insertMember(session.getSessionId(), memberId, memberId.equals(ownerId) ? 1 : 0);
        }
        return session.getSessionId();
    }

    @Override
    public void assertMember(Long userId, Long sessionId) {
        if (sessionId == null || !memberMapper.existsMember(sessionId, userId)) {
            throw exception(RESOURCE_OWNER_MISMATCH);
        }
    }

    @Override
    public List<Long> listMemberIds(Long sessionId) {
        return memberMapper.selectListBySession(sessionId).stream()
                .map(ImSessionMemberDO::getUserId)
                .toList();
    }

    @Override
    public void markRead(Long userId, Long sessionId, Long seq) {
        if (seq == null || seq <= 0) {
            return;
        }
        memberMapper.advanceLastReadSeq(sessionId, userId, seq);
    }

    private void insertMember(Long sessionId, Long userId, Integer role) {
        memberMapper.insert(ImSessionMemberDO.builder()
                .sessionId(sessionId).userId(userId).role(role).lastReadSeq(0L).build());
    }

    private SessionItem toItem(ImSessionDO session, ImSessionMemberDO membership) {
        Long lastReadSeq = membership == null ? 0L : membership.getLastReadSeq();
        Long unread = membership == null ? 0L
                : messageMapper.countUnread(session.getSessionId(), lastReadSeq, membership.getUserId());
        return new SessionItem(session.getSessionId(), session.getType(), session.getDemandId(),
                session.getMatchId(), session.getMemberCount(), session.getMaxSequence(), session.getStatus(),
                session.getUpdateTime(), unread);
    }

    private int normalizeSize(int size) {
        if (size <= 0) {
            return 20;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }

    private long toMillis(LocalDateTime time) {
        return time == null ? 0L : time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    private String buildCursor(SessionItem item) {
        return toMillis(item.lastActiveTime()) + CURSOR_SEPARATOR + item.sessionId();
    }

    /** 游标非法时按"从头开始"处理，不报 500 */
    private long[] parseCursor(String cursor) {
        if (StrUtil.isBlank(cursor)) {
            return null;
        }
        String[] parts = cursor.split(CURSOR_SEPARATOR);
        if (parts.length != 2) {
            return null;
        }
        try {
            return new long[]{Long.parseLong(parts[0]), Long.parseLong(parts[1])};
        } catch (NumberFormatException ex) {
            throw exception(PARAM_INVALID);
        }
    }
}
