package com.iot.platform.device.controller;

import com.iot.platform.common.R;
import com.iot.platform.device.dto.IotDeviceGroupDTO;
import com.iot.platform.device.service.IotDeviceGroupService;
import com.iot.platform.device.vo.IotDeviceGroupVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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