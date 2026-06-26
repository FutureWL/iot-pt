package com.iot.platform.rule.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.iot.platform.common.R;
import com.iot.platform.rule.dto.HandleAlertDTO;
import com.iot.platform.rule.dto.IotAlertQueryDTO;
import com.iot.platform.rule.service.AlertService;
import com.iot.platform.rule.vo.IotAlertVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "告警")
@RestController
@RequestMapping("/alert")
@RequiredArgsConstructor
public class AlertController {

    private final AlertService alertService;

    @Operation(summary = "分页列表")
    @GetMapping("/page")
    public R<IPage<IotAlertVO>> page(IotAlertQueryDTO q) {
        return R.ok(alertService.page(q));
    }

    @Operation(summary = "详情")
    @GetMapping("/{id}")
    public R<IotAlertVO> detail(@PathVariable Long id) {
        return R.ok(alertService.detail(id));
    }

    @Operation(summary = "处理/忽略")
    @PutMapping("/{id}/handle")
    public R<Void> handle(@PathVariable Long id, @Valid @RequestBody HandleAlertDTO dto) {
        alertService.handle(id, dto);
        return R.ok();
    }

    @Operation(summary = "统计(状态/级别)")
    @GetMapping("/stats")
    public R<Map<String, Object>> stats() {
        return R.ok(alertService.stats());
    }
}