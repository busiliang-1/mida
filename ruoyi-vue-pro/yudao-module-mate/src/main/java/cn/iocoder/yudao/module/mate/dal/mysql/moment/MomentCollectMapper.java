package cn.iocoder.yudao.module.mate.dal.mysql.moment;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.mate.dal.dataobject.moment.MomentCollectDO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;
import java.util.List;

/**
 * M5 收藏 Mapper。语义与点赞一致：唯一键防重、取消走物理 DELETE。
 */
@Mapper
public interface MomentCollectMapper extends BaseMapperX<MomentCollectDO> {

    default MomentCollectDO selectByPostAndUser(Long postId, Long userId) {
        return selectOne(new LambdaQueryWrapperX<MomentCollectDO>()
                .eq(MomentCollectDO::getPostId, postId)
                .eq(MomentCollectDO::getUserId, userId));
    }

    @Delete("DELETE FROM moment_collect WHERE post_id = #{postId} AND user_id = #{userId}")
    int deleteByPostAndUser(@Param("postId") Long postId, @Param("userId") Long userId);

    @Select("""
            <script>
            SELECT post_id FROM moment_collect
            WHERE user_id = #{userId} AND post_id IN
            <foreach collection="postIds" item="pid" open="(" separator="," close=")">#{pid}</foreach>
            </script>
            """)
    List<Long> selectCollectedPostIds(@Param("userId") Long userId, @Param("postIds") Collection<Long> postIds);

    @Select("SELECT COUNT(*) FROM moment_collect WHERE post_id = #{postId}")
    Long countByPost(@Param("postId") Long postId);

    @Select("""
            <script>
            SELECT post_id AS id, COUNT(*) AS cnt FROM moment_collect
            WHERE post_id IN
            <foreach collection="postIds" item="pid" open="(" separator="," close=")">#{pid}</foreach>
            GROUP BY post_id
            </script>
            """)
    List<AggCount> countGroupByPostIds(@Param("postIds") Collection<Long> postIds);

    /** 我收藏的动态 ID，倒序（收藏时间≈id 单调） */
    @Select("""
            <script>
            SELECT post_id FROM moment_collect
            WHERE user_id = #{userId}
              <if test="beforeId != null"> AND id &lt; #{beforeId} </if>
            ORDER BY id DESC
            LIMIT #{limit}
            </script>
            """)
    List<Long> selectMyCollectedPostIds(@Param("userId") Long userId,
                                        @Param("beforeId") Long beforeId,
                                        @Param("limit") int limit);
}
