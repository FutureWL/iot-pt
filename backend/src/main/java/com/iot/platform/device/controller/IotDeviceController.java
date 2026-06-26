package com.iot.platform.device.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.iot.platform.common.R;
import com.iot.platform.device.dto.IotDeviceDTO;
import com.iot.platform.device.dto.IotDeviceQueryDTO;
import com.iot.platform.device.service.IotDeviceService;
import com.iot.platform.device.vo.IotDeviceVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "设备管理")
@RestController
@RequestMapping("/iot/device")
@RequiredArgsConstructor
public class IotDeviceController {

    private final IotDeviceService deviceService;

    @Operation(summary = "分页列表")
    @GetMapping("/page")
    public R<IPage<IotDeviceVO>> page(IotDeviceQueryDTO query) {
        return R.ok(deviceService.page(query));
    }

    @Operation(summary = "详情(脱敏)")
    @GetMapping("/{id}")
    public R<IotDeviceVO> detail(@PathVariable Long id) {
        return R.ok(deviceService.detail(id, false));
    }

    @Operation(summary = "详情(完整密钥)")
    @GetMapping("/{id}/full")
    public R<IotDeviceVO> detailFull(@PathVariable Long id) {
        return R.ok(deviceService.detail(id, true));
    }

    @Operation(summary = "新建")
    @PostMapping
    public R<IotDeviceVO> create(@Valid @RequestBody IotDeviceDTO dto) {
        return R.ok(deviceService.create(dto));
    }

    @Operation(summary = "更新")
    @PutMapping
    public R<Void> update(@Valid @RequestBody IotDeviceDTO dto) {
        deviceService.update(dto);
        return R.ok();
    }

    @Operation(summary = "删除")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        deviceService.delete(id);
        return R.ok();
    }

    @Operation(summary = "重置设备密钥")
    @PostMapping("/{id}/reset-secret")
    public R<String> resetSecret(@PathVariable Long id) {
        return R.ok("操作成功", deviceService.resetSecret(id));
    }

    @Operation(summary = "启/停/禁用")
    @PutMapping("/{id}/status/{status}")
    public R<Void> toggleStatus(@PathVariable Long id,
                                @PathVariable Integer status) {
        deviceService.toggleStatus(id, status);
        return R.ok();
    }
}