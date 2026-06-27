package com.iot.platform.device.service;

import com.iot.platform.device.dto.IotDeviceShadowDTO;
import com.iot.platform.device.vo.IotDeviceShadowVO;

import java.util.List;

public interface IotDeviceShadowService {
    /** 获取设备的所有属性当前值(对应物模型) */
    List<IotDeviceShadowVO> list(Long deviceId);

    /** 上报 / 写入一个属性 */
    void upsert(Long deviceId, IotDeviceShadowDTO dto);

    /** 清空一个属性 */
    void deleteOne(Long deviceId, String identifier);
}