package com.iot.platform.monitor.topology.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.iot.platform.monitor.topology.entity.TopologyNode;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface TopologyNodeMapper extends BaseMapper<TopologyNode> {
}
