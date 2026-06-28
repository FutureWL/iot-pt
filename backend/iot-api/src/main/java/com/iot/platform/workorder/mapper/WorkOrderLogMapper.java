package com.iot.platform.workorder.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.iot.platform.workorder.entity.WorkOrderLog;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface WorkOrderLogMapper extends BaseMapper<WorkOrderLog> {
}
