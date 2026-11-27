package cn.iocoder.yudao.module.mate.dal.mysql.moment;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 审核队列 Mapper（跨 moment_post / moment_comment 的 UNION 查询）。
 * 不继承 BaseMapperX：这里没有单一实体，只是一条只读的跨表投影查询。
 */
@Mapper
public interface MomentAuditMapper {

    @Select("""
            <script>
            SELECT t.* FROM (
                SELECT 'post' AS biz_type, p.post_id AS biz_id, p.user_id AS user_id,
                       p.content AS content, p.audit_status AS audit_status, p.create_time AS create_time
                FROM moment_post p
                WHERE p.deleted = b'0'
                <if test="status != null"> AND p.audit_status = #{status} </if>
                <if test="status == null"> AND p.audit_status IN (0, 3) </if>
                UNION ALL
                SELECT 'comment' AS biz_type, c.comment_id AS biz_id, c.user_id AS user_id,
                       c.content AS content, c.audit_status AS audit_status, c.create_time AS create_time
                FROM moment_comment c
                WHERE c.deleted = b'0'
                <if test="status != null"> AND c.audit_status = #{status} </if>
                <if test="status == null"> AND c.audit_status IN (0, 3) </if>
            ) t
            <if test="beforeId != null"> WHERE t.biz_id &lt; #{beforeId} </if>
            ORDER BY t.biz_id DESC
            LIMIT #{limit}
            </script>
            """)
    List<AuditQueueRow> selectQueue(@Param("status") Integer status,
                                    @Param("beforeId") Long beforeId,
                                    @Param("limit") int limit);
}
