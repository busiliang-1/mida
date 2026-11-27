package cn.iocoder.yudao.module.mate.dal.mysql.moment;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.mate.dal.dataobject.moment.MomentPostDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * M5 动态 Mapper。
 * 所有信息流查询都必须带 publish_status = 1（已发布），这样"待审内容不对外"由 SQL 保证，
 * 不依赖 Service 层记得过滤。
 * 游标统一用 post_id（雪花 ID 单调递增≈发布时间序），不用 offset，避免翻页抖动与深翻全表扫。
 */
@Mapper
public interface MomentPostMapper extends BaseMapperX<MomentPostDO> {

    /**
     * 推荐流：已发布 + 可见性过滤，按 post_id 倒序游标。
     * 可见性三态：公开谁都能看；同校需要 viewer 与作者同 school_id；仅自己只有作者能看。
     *
     * @param schoolId viewer 的学校编号，null 表示未做 L3 认证（同校内容对他不可见）
     */
    @Select("""
            <script>
            SELECT p.* FROM moment_post p
            LEFT JOIN sys_user u ON u.user_id = p.user_id AND u.deleted = b'0'
            WHERE p.publish_status = 1 AND p.deleted = b'0'
              <if test="beforeId != null"> AND p.post_id &lt; #{beforeId} </if>
              AND (p.visible_scope = 0
                   OR p.user_id = #{viewerId}
                   <if test="schoolId != null"> OR (p.visible_scope = 1 AND u.school_id = #{schoolId}) </if>)
            ORDER BY p.post_id DESC
            LIMIT #{limit}
            </script>
            """)
    List<MomentPostDO> selectFeed(@Param("viewerId") Long viewerId,
                                  @Param("schoolId") Integer schoolId,
                                  @Param("beforeId") Long beforeId,
                                  @Param("limit") int limit);

    /** 关注流：只取我关注的人发的已发布内容 */
    @Select("""
            <script>
            SELECT p.* FROM moment_post p
            LEFT JOIN sys_user u ON u.user_id = p.user_id AND u.deleted = b'0'
            WHERE p.publish_status = 1 AND p.deleted = b'0'
              AND p.user_id IN (SELECT f.followee_id FROM user_follow f
                                WHERE f.follower_id = #{viewerId} AND f.deleted = b'0')
              <if test="beforeId != null"> AND p.post_id &lt; #{beforeId} </if>
              AND (p.visible_scope = 0
                   OR p.user_id = #{viewerId}
                   <if test="schoolId != null"> OR (p.visible_scope = 1 AND u.school_id = #{schoolId}) </if>)
            ORDER BY p.post_id DESC
            LIMIT #{limit}
            </script>
            """)
    List<MomentPostDO> selectFollowFeed(@Param("viewerId") Long viewerId,
                                        @Param("schoolId") Integer schoolId,
                                        @Param("beforeId") Long beforeId,
                                        @Param("limit") int limit);

    /** 同校流：只取本校作者的已发布内容；schoolId 为 null 时返回空（未认证没有"本校"） */
    @Select("""
            <script>
            SELECT p.* FROM moment_post p
            INNER JOIN sys_user u ON u.user_id = p.user_id AND u.deleted = b'0'
            WHERE p.publish_status = 1 AND p.deleted = b'0'
              AND u.school_id = #{schoolId}
              <if test="beforeId != null"> AND p.post_id &lt; #{beforeId} </if>
            ORDER BY p.post_id DESC
            LIMIT #{limit}
            </script>
            """)
    List<MomentPostDO> selectSchoolFeed(@Param("schoolId") Integer schoolId,
                                        @Param("beforeId") Long beforeId,
                                        @Param("limit") int limit);

    /** 某人主页的发布列表（含待审，仅作者自己看时用） */
    @Select("""
            <script>
            SELECT * FROM moment_post
            WHERE user_id = #{userId} AND deleted = b'0'
              <if test="onlyPublished"> AND publish_status = 1 </if>
              <if test="beforeId != null"> AND post_id &lt; #{beforeId} </if>
            ORDER BY post_id DESC
            LIMIT #{limit}
            </script>
            """)
    List<MomentPostDO> selectByUser(@Param("userId") Long userId,
                                    @Param("onlyPublished") boolean onlyPublished,
                                    @Param("beforeId") Long beforeId,
                                    @Param("limit") int limit);

    /** 审核队列：待审/转人工优先按 id 正序（先来先审） */
    @Select("""
            <script>
            SELECT * FROM moment_post
            WHERE deleted = b'0'
              <if test="auditStatus != null"> AND audit_status = #{auditStatus} </if>
              <if test="beforeId != null"> AND post_id &lt; #{beforeId} </if>
            ORDER BY post_id DESC
            LIMIT #{limit}
            </script>
            """)
    List<MomentPostDO> selectAuditQueue(@Param("auditStatus") Integer auditStatus,
                                        @Param("beforeId") Long beforeId,
                                        @Param("limit") int limit);

    /**
     * 审核推进：CAS 带前驱状态，避免两个审核员同时处置导致后写覆盖先写。
     * 通过时同步把 publish_status 提到 1（已发布）；驳回保持 0（仅自己可见）。
     */
    @Update("UPDATE moment_post SET audit_status = #{toStatus}, publish_status = #{publishStatus}, "
            + "update_time = NOW() WHERE post_id = #{postId} AND audit_status = #{fromStatus} AND deleted = b'0'")
    int advanceAudit(@Param("postId") Long postId,
                     @Param("fromStatus") Integer fromStatus,
                     @Param("toStatus") Integer toStatus,
                     @Param("publishStatus") Integer publishStatus);

    /** 作者下架：只有本人能把已发布改为已下架 */
    @Update("UPDATE moment_post SET publish_status = 2, update_time = NOW() "
            + "WHERE post_id = #{postId} AND user_id = #{userId} AND publish_status = 1 AND deleted = b'0'")
    int takeDown(@Param("postId") Long postId, @Param("userId") Long userId);
}
