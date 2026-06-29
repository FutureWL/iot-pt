package com.iot.platform.system.dict.controller;
import com.iot.platform.common.R;
import com.iot.platform.system.dict.entity.SysDictItem;
import com.iot.platform.system.dict.entity.SysDictType;
import com.iot.platform.system.dict.service.SysDictService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.HashMap;
import java.util.Map;
@RestController @RequestMapping("/system/dict") @RequiredArgsConstructor
public class SysDictController {
    private final SysDictService service;

    @GetMapping("/type/page") public R<Map<String, Object>> typePage(@RequestParam Map<String, Object> params) {
        return R.ok(service.pageTypes(normalize(params)));
    }
    @GetMapping("/item/page") public R<Map<String, Object>> itemPage(@RequestParam Map<String, Object> params) {
        return R.ok(service.pageItems(normalize(params)));
    }
    @PostMapping("/type") public R<Long> createType(@RequestBody SysDictType dto) { return R.ok(service.createType(dto)); }
    @PostMapping("/item") public R<Long> createItem(@RequestBody SysDictItem dto) { return R.ok(service.createItem(dto)); }
    @PutMapping("/type") public R<Void> updateType(@RequestBody SysDictType dto) { service.updateType(dto); return R.ok(); }
    @PutMapping("/item") public R<Void> updateItem(@RequestBody SysDictItem dto) { service.updateItem(dto); return R.ok(); }
    @DeleteMapping("/type/{id}") public R<Void> deleteType(@PathVariable Long id) { service.deleteType(id); return R.ok(); }
    @DeleteMapping("/item/{id}") public R<Void> deleteItem(@PathVariable Long id) { service.deleteItem(id); return R.ok(); }

    /** 兼容前端 pageNum/pageSize(自动转 current/size) */
    private Map<String, Object> normalize(Map<String, Object> params) {
        Map<String, Object> m = new HashMap<>(params);
        if (params.containsKey("pageNum") && !params.containsKey("current")) m.put("current", params.get("pageNum"));
        if (params.containsKey("pageSize") && !params.containsKey("size")) m.put("size", params.get("pageSize"));
        return m;
    }
}