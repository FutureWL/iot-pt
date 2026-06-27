package com.iot.platform.device.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.iot.platform.device.entity.IotDeviceProperty;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface IotDevicePropertyMapper extends BaseMapper<IotDeviceProperty> {
}