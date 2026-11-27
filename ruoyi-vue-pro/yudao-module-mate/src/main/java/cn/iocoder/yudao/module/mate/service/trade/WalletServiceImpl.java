package cn.iocoder.yudao.module.mate.service.trade;

import cn.iocoder.yudao.module.mate.dal.dataobject.trade.WalletAccountDO;
import cn.iocoder.yudao.module.mate.dal.mysql.trade.WalletAccountMapper;
import cn.iocoder.yudao.module.mate.dal.mysql.trade.WalletTransferMapper;
import cn.iocoder.yudao.module.mate.framework.trade.TransferLimits;
import jakarta.annotation.Resource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.mate.enums.ErrorCodeConstants.BALANCE_INSUFFICIENT;
import static cn.iocoder.yudao.module.mate.enums.ErrorCodeConstants.PARAM_INVALID;

/**
 * M4 钱包实现。
 * 扣款不做"先查余额再扣"：直接依赖 SQL 的 available_amount >= ? 条件，0 行即视为余额不足，
 * 保证并发下余额不会被扣成负数（唯一可信的判断在数据库）。
 */
@Service
@Validated
public class WalletServiceImpl implements WalletService {

    @Resource
    private WalletAccountMapper accountMapper;
    @Resource
    private WalletTransferMapper transferMapper;

    @Override
    public WalletAccountDO getOrCreate(Long userId) {
        if (userId == null) {
            throw exception(PARAM_INVALID);
        }
        WalletAccountDO account = accountMapper.selectByUserId(userId);
        if (account != null) {
            return account;
        }
        account = WalletAccountDO.builder()
                .userId(userId)
                .availableAmount(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP))
                .frozenAmount(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP))
                .version(0)
                .build();
        try {
            accountMapper.insert(account);
            return account;
        } catch (DuplicateKeyException ex) {
            // 并发开户撞 uk_user_id：回查已有账户，不报错
            return accountMapper.selectByUserId(userId);
        }
    }

    @Override
    public BigDecimal debit(Long userId, BigDecimal amount) {
        WalletAccountDO account = getOrCreate(userId);
        int rows = accountMapper.debit(userId, amount, account.getVersion());
        if (rows == 0) {
            // 与硬约束一致：影响行数 0 即余额不足（含并发版本冲突导致的失败）
            throw exception(BALANCE_INSUFFICIENT);
        }
        return account.getAvailableAmount().subtract(amount);
    }

    @Override
    public BigDecimal credit(Long userId, BigDecimal amount) {
        WalletAccountDO account = getOrCreate(userId);
        int rows = accountMapper.credit(userId, amount);
        if (rows == 0) {
            throw exception(BALANCE_INSUFFICIENT);
        }
        return account.getAvailableAmount().add(amount);
    }

    @Override
    public WalletView getWallet(Long userId) {
        WalletAccountDO account = getOrCreate(userId);
        BigDecimal used = transferMapper.sumTodayOutgoing(userId, LocalDate.now().atStartOfDay());
        BigDecimal remain = TransferLimits.DAILY_LIMIT.subtract(used == null ? BigDecimal.ZERO : used);
        if (remain.signum() < 0) {
            remain = BigDecimal.ZERO;
        }
        return new WalletView(account.getAvailableAmount(), account.getFrozenAmount(),
                remain.setScale(2, RoundingMode.HALF_UP));
    }
}
