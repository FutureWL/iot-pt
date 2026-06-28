package com.iot.platform.workorder.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.iot.platform.workorder.dto.WorkOrderDTO;
import com.iot.platform.workorder.dto.WorkOrderQuery;
import com.iot.platform.workorder.entity.WorkOrder;
import com.iot.platform.workorder.entity.WorkOrderLog;
import com.iot.platform.workorder.mapper.WorkOrderLogMapper;
import com.iot.platform.workorder.mapper.WorkOrderMapper;
import com.iot.platform.workorder.service.WorkOrderService;
import com.iot.platform.workorder.vo.WorkOrderLogVO;
import com.iot.platform.workorder.vo.WorkOrderStatsVO;
import com.iot.platform.workorder.vo.WorkOrderVO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class WorkOrderServiceImpl implements WorkOrderService {

    private final WorkOrderMapper workOrderMapper;
    private final WorkOrderLogMapper workOrderLogMapper;

    @Override
    public WorkOrderStatsVO stats() {
        WorkOrderStatsVO vo = new WorkOrderStatsVO();
        vo.setPending(workOrderMapper.countByStatus("PENDING"));
        vo.setProcessing(workOrderMapper.countByStatus("PROCESSING"));
        vo.setCompleted(workOrderMapper.countByStatus("COMPLETED"));
        vo.setOverdue(workOrderMapper.countByStatus("OVERDUE"));
        return vo;
    }

    @Override
    public Map<String, Object> page(WorkOrderQuery query) {
        Page<WorkOrder> page = new Page<>(query.getCurrent(), query.getSize());
        LambdaQueryWrapper<WorkOrder> wrapper = new LambdaQueryWrapper<>();
        if (query.getStatus() != null) wrapper.eq(WorkOrder::getStatus, query.getStatus());
        if (query.getPriority() != null) wrapper.eq(WorkOrder::getPriority, query.getPriority());
        if (query.getAssignee() != null) wrapper.eq(WorkOrder::getAssignee, query.getAssignee());
        if (query.getDeviceId() != null) wrapper.eq(WorkOrder::getDeviceId, query.getDeviceId());
        wrapper.orderByDesc(WorkOrder::getCreatedAt);
        Page<WorkOrder> result = workOrderMapper.selectPage(page, wrapper);
        Map<String, Object> map = new HashMap<>();
        map.put("records", result.getRecords().stream().map(this::toVO).collect(Collectors.toList()));
        map.put("total", result.getTotal());
        map.put("size", result.getSize());
        map.put("current", result.getCurrent());
        return map;
    }

    @Override
    public WorkOrderVO detail(Long id) {
        WorkOrder entity = workOrderMapper.selectById(id);
        return entity == null ? null : toVO(entity);
    }

    @Override
    public List<WorkOrderLogVO> logs(Long workOrderId) {
        LambdaQueryWrapper<WorkOrderLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(WorkOrderLog::getWorkOrderId, workOrderId).orderByDesc(WorkOrderLog::getTs);
        return workOrderLogMapper.selectList(wrapper).stream().map(this::toLogVO).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public Long create(WorkOrderDTO dto) {
        WorkOrder entity = new WorkOrder();
        BeanUtils.copyProperties(dto, entity);
        entity.setWorkOrderNo("WO" + LocalDateTime.now().getYear() + System.currentTimeMillis());
        if (entity.getStatus() == null) entity.setStatus("PENDING");
        workOrderMapper.insert(entity);
        WorkOrderLog log = new WorkOrderLog();
        log.setWorkOrderId(entity.getId());
        log.setOperator(dto.getCreator() != null ? dto.getCreator() : "system");
        log.setAction("CREATE");
        workOrderLogMapper.insert(log);
        return entity.getId();
    }

    @Override
    @Transactional
    public void update(WorkOrderDTO dto) {
        WorkOrder entity = workOrderMapper.selectById(dto.getId());
        if (entity == null) return;
        BeanUtils.copyProperties(dto, entity, "id", "workOrderNo", "createdAt");
        workOrderMapper.updateById(entity);
    }

    @Override
    @Transactional
    public void assign(Long id, String assignee) {
        WorkOrder entity = workOrderMapper.selectById(id);
        if (entity == null) return;
        entity.setAssignee(assignee);
        entity.setStatus("PROCESSING");
        workOrderMapper.updateById(entity);
        WorkOrderLog log = new WorkOrderLog();
        log.setWorkOrderId(id);
        log.setOperator(assignee);
        log.setAction("ASSIGN");
        workOrderLogMapper.insert(log);
    }

    @Override
    @Transactional
    public void complete(Long id, String remark) {
        WorkOrder entity = workOrderMapper.selectById(id);
        if (entity == null) return;
        entity.setStatus("COMPLETED");
        entity.setCompletedAt(LocalDateTime.now());
        workOrderMapper.updateById(entity);
        WorkOrderLog log = new WorkOrderLog();
        log.setWorkOrderId(id);
        log.setOperator(entity.getAssignee());
        log.setAction("COMPLETE");
        log.setRemark(remark);
        workOrderLogMapper.insert(log);
    }

    private WorkOrderVO toVO(WorkOrder e) {
        WorkOrderVO v = new WorkOrderVO();
        BeanUtils.copyProperties(e, v);
        return v;
    }

    private WorkOrderLogVO toLogVO(WorkOrderLog e) {
        WorkOrderLogVO v = new WorkOrderLogVO();
        BeanUtils.copyProperties(e, v);
        return v;
    }
}
