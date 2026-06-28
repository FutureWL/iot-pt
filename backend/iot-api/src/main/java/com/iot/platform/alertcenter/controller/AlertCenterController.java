package com.iot.platform.alertcenter.controller;
import com.iot.platform.alertcenter.service.AlertCenterService;
import com.iot.platform.alertcenter.vo.AlertCenterVO;
import com.iot.platform.alertcenter.vo.AlertLevelStatVO;
import com.iot.platform.common.R;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.HashMap; import java.util.List; import java.util.Map;
@RestController @RequestMapping("/alert/center") @RequiredArgsConstructor
public class AlertCenterController {
    private final AlertCenterService service;
    @GetMapping("/stats") public R<List<AlertLevelStatVO>> stats() { return R.ok(service.stats()); }
    @GetMapping("/page") public R<Map<String, Object>> page(@RequestParam Map<String, Object> params) {
        Map<String, Object> normalized = new HashMap<>(params);
        if (params.containsKey("pageNum") && !params.containsKey("current")) {
            normalized.put("current", params.get("pageNum"));
        }
        if (params.containsKey("pageSize") && !params.containsKey("size")) {
            normalized.put("size", params.get("pageSize"));
        }
        return R.ok(service.page(normalized));
    }
}
