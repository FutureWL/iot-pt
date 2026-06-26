package com.iot.platform.device.controller;

import com.iot.platform.common.R;
import com.iot.platform.device.dto.IotDeviceShadowDTO;
import com.iot.platform.device.service.IotDeviceShadowService;
import com.iot.platform.device.vo.IotDeviceShadowVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "设备影子")
@RestController
@RequestMapping("/iot/device-shadow")
@RequiredArgsConstructor
public class IotDeviceShadowController {

    private final IotDeviceShadowService shadowService;

    @Operation(summary = "查看设备所有属性(按物模型)")
    @GetMapping("/{deviceId}")
    public R<List<IotDeviceShadowVO>> list(@PathVariable Long deviceId) {
        return R.ok(shadowService.list(deviceId));
    }

    @Operation(summary = "上报/写入一个属性")
    @PostMapping("/{deviceId}")
    public R<Void> upsert(@PathVariable Long deviceId,
                          @Valid @RequestBody IotDeviceShadowDTO dto) {
        shadowService.upsert(deviceId, dto);
        return R.ok();
    }

    @Operation(summary = "清除一个属性的影子值")
    @DeleteMapping("/{deviceId}/{identifier}")
    public R<Void> deleteOne(@PathVariable Long deviceId,
                             @PathVariable String identifier) {
        shadowService.deleteOne(deviceId, identifier);
        return R.ok();
    }
}