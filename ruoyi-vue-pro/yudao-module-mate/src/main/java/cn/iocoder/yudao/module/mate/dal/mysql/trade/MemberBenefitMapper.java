package cn.iocoder.yudao.module.mate.dal.mysql.trade;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.mate.dal.dataobject.trade.MemberBenefitDO;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface MemberBenefitMapper extends BaseMapperX<MemberBenefitDO> {

    default MemberBenefitDO selectActive(Long userId, String benefitKey, LocalDateTime now) {
        return selectFirstOne(new LambdaQueryWrapperX<MemberBenefitDO>()
                .eq(MemberBenefitDO::getUserId, userId)
                .eq(MemberBenefitDO::getBenefitKey, benefitKey)
                .eq(MemberBenefitDO::getStatus, 0)
                .and(true, w -> w.isNull(MemberBenefitDO::getExpireAt)
                        .or().gt(MemberBenefitDO::getExpireAt, now))
                .orderByDesc(MemberBenefitDO::getBenefitId));
    }

    default List<MemberBenefitDO> selectListActive(Long userId) {
        return selectList(new LambdaQueryWrapperX<MemberBenefitDO>()
                .eq(MemberBenefitDO::getUserId, userId)
                .eq(MemberBenefitDO::getStatus, 0));
    }
}
