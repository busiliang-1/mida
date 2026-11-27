package cn.iocoder.yudao.module.mate.dal.mysql.trade;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.mate.dal.dataobject.trade.WalletAccountDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;

@Mapper
public interface WalletAccountMapper extends BaseMapperX<WalletAccountDO> {

    default WalletAccountDO selectByUserId(Long userId) {
        return selectOne(new LambdaQueryWrapperX<WalletAccountDO>()
                .eq(WalletAccountDO::getUserId, userId));
    }

    /**
     * 扣款：唯一入口。带乐观锁 + 余额下限双条件，影响行数 0 即余额不足或版本冲突，由上层抛 3001。
     * 禁止任何 UPDATE wallet_account SET available_amount = ? 的写法，避免并发覆盖。
     */
    @Update("UPDATE wallet_account SET available_amount = available_amount - #{amount}, "
            + "version = version + 1, update_time = NOW() "
            + "WHERE user_id = #{userId} AND version = #{version} AND available_amount >= #{amount}")
    int debit(@Param("userId") Long userId, @Param("amount") BigDecimal amount, @Param("version") Integer version);

    /** 入账：纯增量，无需版本比对（不会覆盖他人写入） */
    @Update("UPDATE wallet_account SET available_amount = available_amount + #{amount}, "
            + "version = version + 1, update_time = NOW() WHERE user_id = #{userId}")
    int credit(@Param("userId") Long userId, @Param("amount") BigDecimal amount);
}
