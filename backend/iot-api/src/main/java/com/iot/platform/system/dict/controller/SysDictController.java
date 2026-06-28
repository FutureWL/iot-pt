package com.iot.platform.system.dict.controller;
import com.iot.platform.common.R;
import com.iot.platform.system.dict.service.SysDictService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
@RestController @RequestMapping("/system/dict") @RequiredArgsConstructor
public class SysDictController {
    private final SysDictService service;
    @GetMapping("/type/page") public R<Map<String, Object>> typePage(@RequestParam Map<String, Object> params) { return R.ok(service.pageTypes(params)); }
}
