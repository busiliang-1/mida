package cn.iocoder.yudao.module.mate.dal.mysql.moment;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.mate.dal.dataobject.moment.UserFollowDO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * M5 关注 Mapper。uk_follower_followee 唯一键防重，取关走物理 DELETE
 * （否则"取关后再关注"会撞唯一键）。
 */
@Mapper
public interface UserFollowMapper extends BaseMapperX<UserFollowDO> {

    default UserFollowDO selectByFollowerAndFollowee(Long followerId, Long followeeId) {
        return selectOne(new LambdaQueryWrapperX<UserFollowDO>()
                .eq(UserFollowDO::getFollowerId, followerId)
                .eq(UserFollowDO::getFolloweeId, followeeId));
    }

    @Delete("DELETE FROM user_follow WHERE follower_id = #{followerId} AND followee_id = #{followeeId}")
    int deleteByFollowerAndFollowee(@Param("followerId") Long followerId, @Param("followeeId") Long followeeId);

    default Long countFollowing(Long followerId) {
        return selectCount(new LambdaQueryWrapperX<UserFollowDO>()
                .eq(UserFollowDO::getFollowerId, followerId));
    }

    default Long countFollowers(Long followeeId) {
        return selectCount(new LambdaQueryWrapperX<UserFollowDO>()
                .eq(UserFollowDO::getFolloweeId, followeeId));
    }
}
