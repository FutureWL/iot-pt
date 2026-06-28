package com.iot.platform.report.service.impl;
import com.iot.platform.report.service.ReportService;
import com.iot.platform.report.vo.ReportTemplateVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.*;
@Service @RequiredArgsConstructor
public class ReportServiceImpl implements ReportService {
    @Override public List<ReportTemplateVO> listTemplates() { return Collections.emptyList(); }
    @Override public Map<String, Object> pageGenerated(Map<String, Object> params) {
        Map<String, Object> map = new HashMap<>();
        map.put("records", Collections.emptyList()); map.put("total", 0);
        return map;
    }
}
