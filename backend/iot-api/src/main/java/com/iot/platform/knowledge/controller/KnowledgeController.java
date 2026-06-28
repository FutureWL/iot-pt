package com.iot.platform.knowledge.controller;
import com.iot.platform.common.R;
import com.iot.platform.knowledge.service.KnowledgeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.HashMap; import java.util.Map;
@RestController @RequestMapping("/knowledge") @RequiredArgsConstructor
public class KnowledgeController {
    private final KnowledgeService service;
    @GetMapping("/page") public R<Map<String, Object>> page(@RequestParam Map<String, Object> params) {
        Map<String, Object> normalized = new HashMap<>(params);
        if (params.containsKey("pageNum") && !params.containsKey("current")) normalized.put("current", params.get("pageNum"));
        if (params.containsKey("pageSize") && !params.containsKey("size")) normalized.put("size", params.get("pageSize"));
        return R.ok(service.page(normalized));
    }
}
