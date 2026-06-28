package com.iot.platform.device.overview.service;

import com.iot.platform.device.overview.vo.DeviceOverviewItemVO;
import com.iot.platform.device.overview.vo.DeviceOverviewStatsVO;
import com.iot.platform.device.overview.vo.ProductDistributionVO;

import java.util.List;

public interface DeviceOverviewService {
    DeviceOverviewStatsVO stats();
    List<ProductDistributionVO> productDistribution();
    List<DeviceOverviewItemVO> listDevices(String keyword, Integer status);
}
