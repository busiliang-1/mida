package cn.iocoder.yudao.module.mate.service.im.validator;

import cn.iocoder.yudao.module.mate.dal.dataobject.trade.WalletTransferDO;
import cn.iocoder.yudao.module.mate.dal.mysql.trade.WalletTransferMapper;
import cn.iocoder.yudao.module.mate.enums.im.ImCardTypeEnum;
import cn.iocoder.yudao.module.mate.enums.trade.TransferStatusEnum;
import cn.iocoder.yudao.module.mate.service.im.CardValidator;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.mate.enums.ErrorCodeConstants.RESOURCE_OWNER_MISMATCH;

/**
 * 转账卡片校验器（cardType=2，bizNo=transferNo）。
 * 只有付款方/收款方能发这张卡；未到账/已撤销的单子仍可展示（历史卡片），但不能凭空捏造单号。
 */
@Component
public class TransferCardValidator implements CardValidator {

    @Resource
    private WalletTransferMapper transferMapper;

    @Override
    public boolean supports(Integer cardType) {
        return Objects.equals(ImCardTypeEnum.TRANSFER.getType(), cardType);
    }

    @Override
    public void validate(Integer cardType, String bizNo, Long senderId) {
        WalletTransferDO transfer = transferMapper.selectByNo(bizNo);
        if (transfer == null || !isParticipant(transfer, senderId)) {
            throw exception(RESOURCE_OWNER_MISMATCH);
        }
    }

    @Override
    public Map<String, Object> render(Integer cardType, String bizNo) {
        WalletTransferDO transfer = transferMapper.selectByNo(bizNo);
        if (transfer == null) {
            return null;
        }
        Map<String, Object> card = new LinkedHashMap<>();
        card.put("cardType", ImCardTypeEnum.TRANSFER.getType());
        card.put("bizNo", transfer.getTransferNo());
        card.put("amount", transfer.getAmount());
        card.put("status", transfer.getStatus());
        card.put("statusText", transfer.getStatus() == null ? null
                : TransferStatusEnum.of(transfer.getStatus()).getName());
        card.put("remark", transfer.getRemark());
        card.put("createTime", transfer.getCreateTime());
        return card;
    }

    private boolean isParticipant(WalletTransferDO transfer, Long userId) {
        return Objects.equals(transfer.getPayerId(), userId) || Objects.equals(transfer.getPayeeId(), userId);
    }
}
