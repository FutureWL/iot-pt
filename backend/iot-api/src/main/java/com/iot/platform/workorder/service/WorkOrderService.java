package com.iot.platform.workorder.service;

import com.iot.platform.workorder.dto.WorkOrderDTO;
import com.iot.platform.workorder.dto.WorkOrderQuery;
import com.iot.platform.workorder.vo.WorkOrderLogVO;
import com.iot.platform.workorder.vo.WorkOrderStatsVO;
import com.iot.platform.workorder.vo.WorkOrderVO;

import java.util.List;
import java.util.Map;

public interface WorkOrderService {
    WorkOrderStatsVO stats();

    Map<String, Object> page(WorkOrderQuery query);

    WorkOrderVO detail(Long id);

    List<WorkOrderLogVO> logs(Long workOrderId);

    Long create(WorkOrderDTO dto);

    void update(WorkOrderDTO dto);

    void assign(Long id, String assignee);

    void complete(Long id, String remark);
}
