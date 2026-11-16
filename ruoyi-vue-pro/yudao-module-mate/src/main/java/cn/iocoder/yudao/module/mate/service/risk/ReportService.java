package cn.iocoder.yudao.module.mate.service.risk;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.mate.dal.dataobject.risk.RiskReportDO;

/**
 * M6 举报工单 + 黑名单 Service。
 */
public interface ReportService {

    Long createReport(Long reporterId, Integer targetType, Long targetId, Integer reason, String evidenceRef);

    /** 处置工单：action=1处置(可拉黑对象)2驳回 */
    void reviewReport(Long reportId, Integer action, String operator);

    PageResult<RiskReportDO> pageReport(PageParam pageParam, Integer status);

    void addBlacklist(Long userId, Integer type, String reason);

    void removeBlacklist(Long userId);
}
