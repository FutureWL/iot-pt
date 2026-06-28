package com.iot.platform.device.service;

import com.iot.platform.device.dto.IotDeviceGroupDTO;
import com.iot.platform.device.dto.IotDeviceGroupQueryDTO;
import com.iot.platform.device.vo.IotDeviceGroupVO;

import java.util.List;

public interface IotDeviceGroupService {
    /** 全部分组(含设备数),供下拉/缓存用 */
    List<IotDeviceGroupVO> all();
    /** 分页查询(供 CrudList 使用) */
    List<IotDeviceGroupVO> page(IotDeviceGroupQueryDTO query);
    /** 分页总数 */
    long countPage(IotDeviceGroupQueryDTO query);
    void create(IotDeviceGroupDTO dto);
    void update(IotDeviceGroupDTO dto);
    void delete(Long id);
}