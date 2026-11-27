package cn.iocoder.yudao.module.mate.service.im;

import cn.iocoder.yudao.module.mate.enums.im.ImCardTypeEnum;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.mate.enums.ErrorCodeConstants.PARAM_INVALID;
import static cn.iocoder.yudao.module.mate.enums.ErrorCodeConstants.RESOURCE_OWNER_MISMATCH;

/**
 * 卡片校验/渲染实现：按 cardType 找到域内校验器，找不到即视为"不支持的类型"，直接 1001/1004，不放行。
 */
@Service
@Validated
public class ImCardServiceImpl implements ImCardService {

    @Resource
    private List<CardValidator> validators;

    @Override
    public void validateOrThrow(Integer cardType, String bizNo, Long senderId) {
        if (ImCardTypeEnum.of(cardType) == null) {
            throw exception(PARAM_INVALID);
        }
        CardValidator validator = findValidator(cardType);
        if (validator == null) {
            // 该类型尚未接入校验器（如 M4/M7 未实现时）：宁可拒绝，也不允许未经校验的卡片进会话
            throw exception(RESOURCE_OWNER_MISMATCH);
        }
        validator.validate(cardType, bizNo, senderId);
    }

    @Override
    public Map<String, Object> render(Integer cardType, String bizNo) {
        CardValidator validator = findValidator(cardType);
        if (validator == null) {
            return null;
        }
        return validator.render(cardType, bizNo);
    }

    private CardValidator findValidator(Integer cardType) {
        return validators.stream()
                .filter(v -> v.supports(cardType))
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }
}
