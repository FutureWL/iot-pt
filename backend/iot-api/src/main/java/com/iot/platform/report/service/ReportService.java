package com.iot.platform.report.service;
import com.iot.platform.report.vo.ReportTemplateVO;
import java.util.List; import java.util.Map;
public interface ReportService {
    List<ReportTemplateVO> listTemplates();
    Map<String, Object> pageGenerated(Map<String, Object> params);
}
