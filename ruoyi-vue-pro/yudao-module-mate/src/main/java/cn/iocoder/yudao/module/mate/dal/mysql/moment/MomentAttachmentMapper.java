package cn.iocoder.yudao.module.mate.dal.mysql.moment;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.mate.dal.dataobject.moment.MomentAttachmentDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;
import java.util.List;

/**
 * M5 动态附件 Mapper。附件只追加，不做更新/删除（契约说明：只追加）。
 */
@Mapper
public interface MomentAttachmentMapper extends BaseMapperX<MomentAttachmentDO> {

    @Select("""
            <script>
            SELECT * FROM moment_attachment
            WHERE post_id IN
            <foreach collection="postIds" item="pid" open="(" separator="," close=")">#{pid}</foreach>
              AND deleted = b'0'
            ORDER BY post_id, sort_no
            </script>
            """)
    List<MomentAttachmentDO> selectByPostIds(@Param("postIds") Collection<Long> postIds);

    default List<MomentAttachmentDO> selectByPostId(Long postId) {
        return selectList(new LambdaQueryWrapperX<MomentAttachmentDO>()
                .eq(MomentAttachmentDO::getPostId, postId)
                .orderByAsc(MomentAttachmentDO::getSortNo));
    }
}
