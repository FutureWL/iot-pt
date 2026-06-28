package com.iot.platform.workorder.controller;

import com.iot.platform.common.R;
import com.iot.platform.workorder.dto.WorkOrderDTO;
import com.iot.platform.workorder.dto.WorkOrderQuery;
import com.iot.platform.workorder.service.WorkOrderService;
import com.iot.platform.workorder.vo.WorkOrderLogVO;
import com.iot.platform.workorder.vo.WorkOrderStatsVO;
import com.iot.platform.workorder.vo.WorkOrderVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/workorder")
@RequiredArgsConstructor
public class WorkorderController {

    private final WorkOrderService workOrderService;

    @GetMapping("/stats")
    public R<WorkOrderStatsVO> stats() {
        return R.ok(workOrderService.stats());
    }

    @GetMapping("/page")
    public R<Map<String, Object>> page(WorkOrderQuery query) {
        return R.ok(workOrderService.page(query));
    }

    @GetMapping("/{id}")
    public R<WorkOrderVO> detail(@PathVariable Long id) {
        return R.ok(workOrderService.detail(id));
    }

    @GetMapping("/{workOrderId}/logs")
    public R<List<WorkOrderLogVO>> logs(@PathVariable Long workOrderId) {
        return R.ok(workOrderService.logs(workOrderId));
    }

    @PostMapping
    public R<Long> create(@RequestBody WorkOrderDTO dto) {
        return R.ok(workOrderService.create(dto));
    }

    @PutMapping
    public R<Void> update(@RequestBody WorkOrderDTO dto) {
        workOrderService.update(dto);
        return R.ok();
    }

    @PutMapping("/{id}/assign")
    public R<Void> assign(@PathVariable Long id, @RequestBody Map<String, String> body) {
        workOrderService.assign(id, body.get("assignee"));
        return R.ok();
    }

    @PutMapping("/{id}/complete")
    public R<Void> complete(@PathVariable Long id, @RequestBody Map<String, String> body) {
        workOrderService.complete(id, body.get("remark"));
        return R.ok();
    }
}
