package cn.iocoder.yudao.module.mate.dal.mysql.trade;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.mate.dal.dataobject.trade.WalletTransferDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface WalletTransferMapper extends BaseMapperX<WalletTransferDO> {

    default WalletTransferDO selectByNo(String transferNo) {
        return selectOne(new LambdaQueryWrapperX<WalletTransferDO>()
                .eq(WalletTransferDO::getTransferNo, transferNo));
    }

    /**
     * 我的转账：direction=1 转出（payer），2 转入（payee），空为全部。
     * 游标用雪花 transfer_id（单调递增，避免同秒多条时 created_at 排序漏数据）。
     */
    default List<WalletTransferDO> selectMyTransfers(Long userId, Integer direction, Long beforeId, int limit) {
        LambdaQueryWrapperX<WalletTransferDO> wrapper = new LambdaQueryWrapperX<>();
        if (Integer.valueOf(2).equals(direction)) {
            wrapper.eq(WalletTransferDO::getPayeeId, userId);
        } else if (Integer.valueOf(1).equals(direction)) {
            wrapper.eq(WalletTransferDO::getPayerId, userId);
        } else {
            wrapper.and(true, w -> w.eq(WalletTransferDO::getPayerId, userId)
                    .or().eq(WalletTransferDO::getPayeeId, userId));
        }
        wrapper.ltIfPresent(WalletTransferDO::getTransferId, beforeId);
        // 多取一条用于判断 hasMore，与 M3 会话列表同一套游标风格
        return selectListLimitN(wrapper.orderByDesc(WalletTransferDO::getTransferId), limit);
    }

    /** 当日已占用额度：待支付/待到账/已到账都算占用，已撤销释放 */
    @Select("SELECT COALESCE(SUM(amount), 0) FROM wallet_transfer "
            + "WHERE payer_id = #{payerId} AND status IN (0, 1, 2) AND create_time >= #{start}")
    BigDecimal sumTodayOutgoing(@Param("payerId") Long payerId, @Param("start") LocalDateTime start);

    /** 到期待入账的单子（status=1 且 delay_until <= now） */
    default List<WalletTransferDO> selectDue(LocalDateTime now, int limit) {
        return selectListLimitN(new LambdaQueryWrapperX<WalletTransferDO>()
                .eq(WalletTransferDO::getStatus, 1)
                .le(WalletTransferDO::getDelayUntil, now)
                .orderByAsc(WalletTransferDO::getDelayUntil), limit);
    }

    /** 入账 CAS：只有仍处于待到账才能改，避免定时任务与收款确认并发重复入账 */
    @Update("UPDATE wallet_transfer SET status = 2, finished_at = NOW(), update_time = NOW() "
            + "WHERE transfer_id = #{transferId} AND status = 1")
    int markSettled(@Param("transferId") Long transferId);

    /** 撤销 CAS：只有待到账可撤销 */
    @Update("UPDATE wallet_transfer SET status = 3, finished_at = NOW(), update_time = NOW() "
            + "WHERE transfer_id = #{transferId} AND status = 1")
    int markCancelled(@Param("transferId") Long transferId);

    /** 直接到账（无延迟）：待支付 → 已到账 */
    @Update("UPDATE wallet_transfer SET status = 2, finished_at = NOW(), update_time = NOW() "
            + "WHERE transfer_id = #{transferId} AND status = 0")
    int markSettledFromWaiting(@Param("transferId") Long transferId);
}
