package com.iot.platform.rule.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.iot.platform.common.R;
import com.iot.platform.rule.dto.IotRuleDTO;
import com.iot.platform.rule.dto.IotRuleQueryDTO;
import com.iot.platform.rule.service.RuleService;
import com.iot.platform.rule.vo.IotRuleVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "规则管理")
@RestController
@RequestMapping("/rule")
@RequiredArgsConstructor
public class RuleController {

    private final RuleService ruleService;

    @Operation(summary = "分页列表")
    @GetMapping("/page")
    public R<IPage<IotRuleVO>> page(IotRuleQueryDTO q) {
        return R.ok(ruleService.page(q));
    }

    @Operation(summary = "详情")
    @GetMapping("/{id}")
    public R<IotRuleVO> detail(@PathVariable Long id) {
        return R.ok(ruleService.detail(id));
    }

    @Operation(summary = "新建")
    @PostMapping
    public R<Void> create(@Valid @RequestBody IotRuleDTO dto) {
        ruleService.create(dto);
        return R.ok();
    }

    @Operation(summary = "更新")
    @PutMapping
    public R<Void> update(@Valid @RequestBody IotRuleDTO dto) {
        ruleService.update(dto);
        return R.ok();
    }

    @Operation(summary = "删除")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        ruleService.delete(id);
        return R.ok();
    }

    @Operation(summary = "启/停")
    @PutMapping("/{id}/status/{status}")
    public R<Void> toggle(@PathVariable Long id, @PathVariable Integer status) {
        ruleService.toggle(id, status);
        return R.ok();
    }
}