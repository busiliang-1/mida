package cn.iocoder.yudao.module.mate.service.risk;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.mate.dal.dataobject.risk.RiskBlacklistDO;
import cn.iocoder.yudao.module.mate.dal.dataobject.risk.RiskReportDO;
import cn.iocoder.yudao.module.mate.dal.mysql.risk.RiskBlacklistMapper;
import cn.iocoder.yudao.module.mate.dal.mysql.risk.RiskReportMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.mate.enums.ErrorCodeConstants.REPORT_NOT_EXISTS;
import static cn.iocoder.yudao.module.mate.enums.ErrorCodeConstants.RESOURCE_STATUS_INVALID;

@Service
public class ReportServiceImpl implements ReportService {

    @Resource
    private RiskReportMapper reportMapper;
    @Resource
    private RiskBlacklistMapper blacklistMapper;

    @Override
    public Long createReport(Long reporterId, Integer targetType, Long targetId, Integer reason, String evidenceRef) {
        RiskReportDO report = RiskReportDO.builder()
                .reporterId(reporterId).targetType(targetType).targetId(targetId)
                .reason(reason).evidenceRef(evidenceRef).status(0)
                .deadlineAt(LocalDateTime.now().plusHours(24))
                .build();
        reportMapper.insert(report);
        return report.getReportId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reviewReport(Long reportId, Integer action, String operator) {
        RiskReportDO report = reportMapper.selectById(reportId);
        if (report == null) {
            throw exception(REPORT_NOT_EXISTS);
        }
        if (report.getStatus() == 2 || report.getStatus() == 3) {
            throw exception(RESOURCE_STATUS_INVALID); // 已终态
        }
        RiskReportDO up = new RiskReportDO();
        up.setReportId(reportId);
        up.setStatus(action == 1 ? 2 : 3);
        reportMapper.updateById(up);
        // 处置且对象为用户 → 拉黑账号
        if (action == 1 && report.getTargetType() == 1) {
            addBlacklist(report.getTargetId(), 3, "举报查实:" + operator);
        }
    }

    @Override
    public PageResult<RiskReportDO> pageReport(PageParam pageParam, Integer status) {
        return reportMapper.selectPage(pageParam, status);
    }

    @Override
    public void addBlacklist(Long userId, Integer type, String reason) {
        blacklistMapper.insert(RiskBlacklistDO.builder()
                .userId(userId).type(type).reason(reason)
                .build());
    }

    @Override
    public void removeBlacklist(Long userId) {
        blacklistMapper.delete(new LambdaQueryWrapper<RiskBlacklistDO>()
                .eq(RiskBlacklistDO::getUserId, userId));
    }
}
