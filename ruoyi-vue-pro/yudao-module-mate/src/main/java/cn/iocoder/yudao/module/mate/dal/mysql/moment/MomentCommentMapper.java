package cn.iocoder.yudao.module.mate.dal.mysql.moment;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.mate.dal.dataobject.moment.MomentCommentDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.Collection;
import java.util.List;

/**
 * M5 评论 Mapper。
 * 评论同样先审后发：列表只返回"已通过"的评论，外加 viewer 自己的待审评论（自己能看到"审核中"）。
 */
@Mapper
public interface MomentCommentMapper extends BaseMapperX<MomentCommentDO> {

    @Select("""
            <script>
            SELECT * FROM moment_comment
            WHERE post_id = #{postId} AND deleted = b'0'
              AND (audit_status = 1 OR user_id = #{viewerId})
              <if test="beforeId != null"> AND comment_id &lt; #{beforeId} </if>
            ORDER BY comment_id DESC
            LIMIT #{limit}
            </script>
            """)
    List<MomentCommentDO> selectApproved(@Param("postId") Long postId,
                                        @Param("viewerId") Long viewerId,
                                        @Param("beforeId") Long beforeId,
                                        @Param("limit") int limit);

    /** 计数只算已通过的，避免待审评论撑大数字又被驳回 */
    @Select("SELECT COUNT(*) FROM moment_comment WHERE post_id = #{postId} AND audit_status = 1 AND deleted = b'0'")
    Long countApprovedByPost(@Param("postId") Long postId);

    @Select("""
            <script>
            SELECT post_id AS id, COUNT(*) AS cnt FROM moment_comment
            WHERE audit_status = 1 AND deleted = b'0' AND post_id IN
            <foreach collection="postIds" item="pid" open="(" separator="," close=")">#{pid}</foreach>
            GROUP BY post_id
            </script>
            """)
    List<AggCount> countGroupByPostIds(@Param("postIds") Collection<Long> postIds);

    @Update("UPDATE moment_comment SET audit_status = #{toStatus}, update_time = NOW() "
            + "WHERE comment_id = #{commentId} AND audit_status = #{fromStatus} AND deleted = b'0'")
    int advanceAudit(@Param("commentId") Long commentId,
                     @Param("fromStatus") Integer fromStatus,
                     @Param("toStatus") Integer toStatus);
}
