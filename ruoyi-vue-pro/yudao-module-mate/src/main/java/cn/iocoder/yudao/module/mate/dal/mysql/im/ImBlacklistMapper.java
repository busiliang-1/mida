package cn.iocoder.yudao.module.mate.dal.mysql.im;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.mate.dal.dataobject.im.ImBlacklistDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface ImBlacklistMapper extends BaseMapperX<ImBlacklistDO> {

    default boolean existsBlock(Long userId, Long blockedUserId) {
        return selectCount(new LambdaQueryWrapperX<ImBlacklistDO>()
                .eq(ImBlacklistDO::getUserId, userId)
                .eq(ImBlacklistDO::getBlockedUserId, blockedUserId)) > 0;
    }

    /** 双向拉黑：任一方拉黑即视为阻断 */
    default boolean existsEitherDirection(Long userId, Long otherUserId) {
        return existsBlock(userId, otherUserId) || existsBlock(otherUserId, userId);
    }

    default List<ImBlacklistDO> selectListByUser(Long userId) {
        return selectList(new LambdaQueryWrapperX<ImBlacklistDO>()
                .eq(ImBlacklistDO::getUserId, userId));
    }
}
