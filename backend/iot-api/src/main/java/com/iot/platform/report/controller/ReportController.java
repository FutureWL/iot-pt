package com.iot.platform.report.controller;
import com.iot.platform.common.R;
import com.iot.platform.report.service.ReportService;
import com.iot.platform.report.vo.ReportTemplateVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List; import java.util.Map;
@RestController @RequestMapping("/report") @RequiredArgsConstructor
public class ReportController {
    private final ReportService service;
    @GetMapping("/templates") public R<List<ReportTemplateVO>> templates() { return R.ok(service.listTemplates()); }
    @GetMapping("/generated/page") public R<Map<String, Object>> generatedPage(@RequestParam Map<String, Object> params) { return R.ok(service.pageGenerated(params)); }
}
