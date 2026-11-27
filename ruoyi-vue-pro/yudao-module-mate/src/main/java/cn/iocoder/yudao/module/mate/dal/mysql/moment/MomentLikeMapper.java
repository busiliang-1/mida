package cn.iocoder.yudao.module.mate.dal.mysql.moment;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.mate.dal.dataobject.moment.MomentLikeDO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;
import java.util.List;

/**
 * M5 点赞 Mapper。
 * 点赞靠 uk_post_user 唯一键防重：并发两次点赞只有一次 INSERT 成功，另一次抛 DuplicateKeyException。
 * 取消点赞用物理 DELETE 而非逻辑删除——唯一键包含已逻辑删除的行会导致"取消后再点赞"直接撞键，
 * 而点赞记录本身没有留痕价值。
 */
@Mapper
public interface MomentLikeMapper extends BaseMapperX<MomentLikeDO> {

    default MomentLikeDO selectByPostAndUser(Long postId, Long userId) {
        return selectOne(new LambdaQueryWrapperX<MomentLikeDO>()
                .eq(MomentLikeDO::getPostId, postId)
                .eq(MomentLikeDO::getUserId, userId));
    }

    @Delete("DELETE FROM moment_like WHERE post_id = #{postId} AND user_id = #{userId}")
    int deleteByPostAndUser(@Param("postId") Long postId, @Param("userId") Long userId);

    @Select("SELECT COUNT(*) FROM moment_like WHERE post_id = #{postId}")
    Long countByPost(@Param("postId") Long postId);

    /** 批量取计数，避免信息流逐条 COUNT 的 N+1 */
    @Select("""
            <script>
            SELECT post_id AS id, COUNT(*) AS cnt FROM moment_like
            WHERE post_id IN
            <foreach collection="postIds" item="pid" open="(" separator="," close=")">#{pid}</foreach>
            GROUP BY post_id
            </script>
            """)
    List<AggCount> countGroupByPostIds(@Param("postIds") Collection<Long> postIds);

    /** 批量判断"我是否点过赞"，供信息流回填 liked 标志 */
    @Select("""
            <script>
            SELECT post_id FROM moment_like
            WHERE user_id = #{userId} AND post_id IN
            <foreach collection="postIds" item="pid" open="(" separator="," close=")">#{pid}</foreach>
            </script>
            """)
    List<Long> selectLikedPostIds(@Param("userId") Long userId, @Param("postIds") Collection<Long> postIds);
}
