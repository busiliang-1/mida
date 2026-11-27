package cn.iocoder.yudao.module.mate.service.trade;

import cn.iocoder.yudao.module.mate.dal.dataobject.trade.WalletAccountDO;

import java.math.BigDecimal;

/**
 * M4 钱包 Service。余额只允许通过 {@link #debit} / {@link #credit} 变更。
 */
public interface WalletService {

    /** 开户（一人一户），已存在直接返回 */
    WalletAccountDO getOrCreate(Long userId);

    /** 扣款：乐观锁 + 余额下限，失败抛 3001；返回记账后余额 */
    BigDecimal debit(Long userId, BigDecimal amount);

    /** 入账；返回记账后余额 */
    BigDecimal credit(Long userId, BigDecimal amount);

    /** 钱包视图：可用 / 冻结 / 当日剩余额度 */
    WalletView getWallet(Long userId);

    record WalletView(BigDecimal available, BigDecimal frozen, BigDecimal remainQuota) {
    }
}
