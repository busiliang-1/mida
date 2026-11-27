package cn.iocoder.yudao.module.mate.dal.mysql.im;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.mate.dal.dataobject.im.ImSessionMemberDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface ImSessionMemberMapper extends BaseMapperX<ImSessionMemberDO> {

    default ImSessionMemberDO selectBySessionAndUser(Long sessionId, Long userId) {
        return selectOne(new LambdaQueryWrapperX<ImSessionMemberDO>()
                .eq(ImSessionMemberDO::getSessionId, sessionId)
                .eq(ImSessionMemberDO::getUserId, userId));
    }

    default List<ImSessionMemberDO> selectListBySession(Long sessionId) {
        return selectList(new LambdaQueryWrapperX<ImSessionMemberDO>()
                .eq(ImSessionMemberDO::getSessionId, sessionId)
                .orderByAsc(ImSessionMemberDO::getId));
    }

    default List<ImSessionMemberDO> selectListByUser(Long userId) {
        return selectList(new LambdaQueryWrapperX<ImSessionMemberDO>()
                .eq(ImSessionMemberDO::getUserId, userId));
    }

    default boolean existsMember(Long sessionId, Long userId) {
        return selectBySessionAndUser(sessionId, userId) != null;
    }

    /** 已读位点只前进不后退，避免乱序上报把未读数改大 */
    default int advanceLastReadSeq(Long sessionId, Long userId, Long seq) {
        return update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<ImSessionMemberDO>()
                .eq(ImSessionMemberDO::getSessionId, sessionId)
                .eq(ImSessionMemberDO::getUserId, userId)
                .lt(ImSessionMemberDO::getLastReadSeq, seq)
                .set(ImSessionMemberDO::getLastReadSeq, seq));
    }
}
