package com.iot.platform.device.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.iot.platform.device.dto.IotDeviceDTO;
import com.iot.platform.device.dto.IotDeviceQueryDTO;
import com.iot.platform.device.vo.IotDeviceVO;

public interface IotDeviceService {
    IPage<IotDeviceVO> page(IotDeviceQueryDTO q);
    IotDeviceVO detail(Long id, boolean fullSecret);
    IotDeviceVO create(IotDeviceDTO dto);
    void update(IotDeviceDTO dto);
    void delete(Long id);

    /** 重置设备密钥 */
    String resetSecret(Long id);

    /** 启/停(2=禁用/0=离线) */
    void toggleStatus(Long id, Integer status);
}