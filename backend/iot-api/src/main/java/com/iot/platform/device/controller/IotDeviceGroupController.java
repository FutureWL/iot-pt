package com.iot.platform.device.controller;

import com.iot.platform.common.R;
import com.iot.platform.device.dto.IotDeviceGroupDTO;
import com.iot.platform.device.dto.IotDeviceGroupQueryDTO;
import com.iot.platform.device.service.IotDeviceGroupService;
import com.iot.platform.device.vo.IotDeviceGroupVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Tag(name = "设备分组")
@RestController
@RequestMapping("/iot/device-group")
@RequiredArgsConstructor
public class IotDeviceGroupController {

    private final IotDeviceGroupService groupService;

    @Operation(summary = "全部分组(含设备数)")
    @GetMapping("/all")
    public R<List<IotDeviceGroupVO>> all() {
        return R.ok(groupService.all());
    }

    @Operation(summary = "分页查询(供 CrudList 使用)")
    @GetMapping("/page")
    public R<Map<String, Object>> page(IotDeviceGroupQueryDTO query) {
        Map<String, Object> result = new HashMap<>();
        result.put("records", groupService.page(query));
        result.put("total", groupService.countPage(query));
        result.put("current", query.getCurrent());
        result.put("size", query.getSize());
        result.put("pages", groupService.countPage(query) / query.getSize() + 1);
        return R.ok(result);
    }

    @Operation(summary = "新建")
    @PostMapping
    public R<Void> create(@Valid @RequestBody IotDeviceGroupDTO dto) {
        groupService.create(dto);
        return R.ok();
    }

    @Operation(summary = "更新")
    @PutMapping
    public R<Void> update(@Valid @RequestBody IotDeviceGroupDTO dto) {
        groupService.update(dto);
        return R.ok();
    }

    @Operation(summary = "删除")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        groupService.delete(id);
        return R.ok();
    }
}