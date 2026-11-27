package cn.iocoder.yudao.module.mate.dal.mysql.im;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.mate.dal.dataobject.im.ImMessageDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface ImMessageMapper extends BaseMapperX<ImMessageDO> {

    /** 幂等快路径：同会话同 clientMsgId 已存在则直接返回首次结果 */
    default ImMessageDO selectByClientMsgId(Long sessionId, String clientMsgId) {
        return selectOne(new LambdaQueryWrapperX<ImMessageDO>()
                .eq(ImMessageDO::getSessionId, sessionId)
                .eq(ImMessageDO::getClientMsgId, clientMsgId));
    }

    /** 历史：seq 倒序，beforeSeq 为空取最新一页 */
    default List<ImMessageDO> selectHistory(Long sessionId, Long beforeSeq, int size) {
        return selectListLimitN(new LambdaQueryWrapperX<ImMessageDO>()
                .eq(ImMessageDO::getSessionId, sessionId)
                .ltIfPresent(ImMessageDO::getSeq, beforeSeq)
                .orderByDesc(ImMessageDO::getSeq), size);
    }

    /** 未读数：seq 大于已读位点且不是自己发的 */
    default Long countUnread(Long sessionId, Long lastReadSeq, Long selfId) {
        return selectCount(new LambdaQueryWrapperX<ImMessageDO>()
                .eq(ImMessageDO::getSessionId, sessionId)
                .gtIfPresent(ImMessageDO::getSeq, lastReadSeq)
                .ne(ImMessageDO::getSenderId, selfId));
    }
}
