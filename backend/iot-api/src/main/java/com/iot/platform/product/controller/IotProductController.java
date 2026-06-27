package com.iot.platform.product.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.iot.platform.common.R;
import com.iot.platform.product.dto.IotProductDTO;
import com.iot.platform.product.dto.IotProductQueryDTO;
import com.iot.platform.product.service.IotProductService;
import com.iot.platform.product.vo.IotProductVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "产品管理")
@RestController
@RequestMapping("/iot/product")
@RequiredArgsConstructor
public class IotProductController {

    private final IotProductService productService;

    @Operation(summary = "分页列表")
    @GetMapping("/page")
    public R<IPage<IotProductVO>> page(IotProductQueryDTO query) {
        return R.ok(productService.page(query));
    }

    @Operation(summary = "全部(下拉用)")
    @GetMapping("/all")
    public R<List<IotProductVO>> all() {
        return R.ok(productService.all());
    }

    @Operation(summary = "默认物模型模板")
    @GetMapping("/thing-model/default")
    public R<String> defaultThingModel() {
        return R.ok(productService.defaultThingModel());
    }

    @Operation(summary = "详情")
    @GetMapping("/{id}")
    public R<IotProductVO> detail(@PathVariable Long id) {
        return R.ok(productService.detail(id));
    }

    @Operation(summary = "新建")
    @PostMapping
    public R<Void> create(@Valid @RequestBody IotProductDTO dto) {
        productService.create(dto);
        return R.ok();
    }

    @Operation(summary = "更新")
    @PutMapping
    public R<Void> update(@Valid @RequestBody IotProductDTO dto) {
        productService.update(dto);
        return R.ok();
    }

    @Operation(summary = "删除")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        productService.delete(id);
        return R.ok();
    }
}