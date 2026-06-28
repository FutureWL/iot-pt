package com.iot.platform.workorder.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.iot.platform.workorder.entity.WorkOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface WorkOrderMapper extends BaseMapper<WorkOrder> {
    @Select("SELECT COUNT(*) FROM work_order WHERE status = #{status}")
    Long countByStatus(@Param("status") String status);
}
