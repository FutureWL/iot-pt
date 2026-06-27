package com.iot.platform.device.service;

import com.iot.platform.device.dto.IotDeviceGroupDTO;
import com.iot.platform.device.vo.IotDeviceGroupVO;

import java.util.List;

public interface IotDeviceGroupService {
    List<IotDeviceGroupVO> all();
    void create(IotDeviceGroupDTO dto);
    void update(IotDeviceGroupDTO dto);
    void delete(Long id);
}