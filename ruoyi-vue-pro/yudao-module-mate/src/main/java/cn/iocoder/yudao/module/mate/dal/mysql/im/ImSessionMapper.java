package cn.iocoder.yudao.module.mate.dal.mysql.im;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.mate.dal.dataobject.im.ImSessionDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface ImSessionMapper extends BaseMapperX<ImSessionDO> {

    default ImSessionDO selectByDemandId(Long demandId) {
        return selectOne(new LambdaQueryWrapperX<ImSessionDO>()
                .eq(ImSessionDO::getDemandId, demandId)
                .eq(ImSessionDO::getType, 2));
    }

    default ImSessionDO selectByMatchId(Long matchId) {
        return selectOne(new LambdaQueryWrapperX<ImSessionDO>()
                .eq(ImSessionDO::getMatchId, matchId)
                .eq(ImSessionDO::getType, 1));
    }

    /**
     * 会话内序列号自增。
     * 与 {@link #selectMaxSequence(Long)} 必须在同一事务内使用：
     * UPDATE 先取到该行的排他锁，后续 SELECT 读到自己刚写的值，并发事务会在 UPDATE 上串行等待，
     * 因此无需额外分布式锁即可保证同一会话 seq 单调递增；uk_session_seq 是最后一道兜底。
     */
    @Update("UPDATE im_session SET max_sequence = max_sequence + 1, update_time = NOW() "
            + "WHERE session_id = #{sessionId}")
    int incrementSequence(@Param("sessionId") Long sessionId);

    @Select("SELECT max_sequence FROM im_session WHERE session_id = #{sessionId}")
    Long selectMaxSequence(@Param("sessionId") Long sessionId);

    default int updateStatus(Long sessionId, Integer status) {
        ImSessionDO update = new ImSessionDO();
        update.setSessionId(sessionId);
        update.setStatus(status);
        return updateById(update);
    }
}
