package cn.iocoder.yudao.module.mate.listener.im;

import cn.iocoder.yudao.module.mate.dal.dataobject.match.MateMatchDO;
import cn.iocoder.yudao.module.mate.dal.mysql.match.MateMatchMapper;
import cn.iocoder.yudao.module.mate.event.DemandFormedEvent;
import cn.iocoder.yudao.module.mate.event.MatchCreatedEvent;
import cn.iocoder.yudao.module.mate.service.im.ImSessionService;
import cn.iocoder.yudao.module.mate.service.match.DemandService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * M3 会话开通监听器。
 * 为什么用事件：M2 只负责"匹配/成团"事实，M3 负责"开会话"结果，两边不互相依赖实现。
 * 注意：会话开通失败不应回滚甲的业务事务，因此监听器内部自行兜住异常并记日志（业务可用重试/补偿补建）。
 */
@Slf4j
@Component
public class ImSessionEventListener {

    @Resource
    private ImSessionService sessionService;
    @Resource
    private DemandService demandService;
    @Resource
    private MateMatchMapper mateMatchMapper;

    @EventListener
    @Transactional(rollbackFor = Exception.class)
    public void onMatchCreated(MatchCreatedEvent event) {
        try {
            MateMatchDO match = mateMatchMapper.selectByLowHigh(event.userLowId(), event.userHighId());
            Long matchId = match == null ? null : match.getMatchId();
            if (matchId == null) {
                log.warn("[onMatchCreated][low({}) high({}) 未查到匹配记录，跳过建会话]",
                        event.userLowId(), event.userHighId());
                return;
            }
            Long sessionId = sessionService.createSingleSession(event.userLowId(), event.userHighId(), matchId);
            log.info("[onMatchCreated][matchId({}) sessionId({}) 单聊就绪]", matchId, sessionId);
        } catch (RuntimeException ex) {
            log.error("[onMatchCreated][low({}) high({}) 建会话失败]",
                    event.userLowId(), event.userHighId(), ex);
        }
    }

    @EventListener
    @Transactional(rollbackFor = Exception.class)
    public void onDemandFormed(DemandFormedEvent event) {
        try {
            List<Long> members = demandService.listSignups(event.demandId()).stream()
                    .filter(s -> Integer.valueOf(1).equals(s.getAuditStatus()))
                    .map(s -> s.getUserId())
                    .toList();
            Long sessionId = sessionService.createGroupSession(event.demandId(), event.publisherId(), members);
            log.info("[onDemandFormed][demandId({}) sessionId({}) 群聊就绪，成员 {} 人]",
                    event.demandId(), sessionId, members.size() + 1);
        } catch (RuntimeException ex) {
            log.error("[onDemandFormed][demandId({}) 建群失败]", event.demandId(), ex);
        }
    }
}
