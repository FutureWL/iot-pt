package com.iot.platform.rule.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.iot.platform.rule.dto.HandleAlertDTO;
import com.iot.platform.rule.dto.IotAlertQueryDTO;
import com.iot.platform.rule.vo.IotAlertVO;

public interface AlertService {
    IPage<IotAlertVO> page(IotAlertQueryDTO q);
    IotAlertVO detail(Long id);
    /** 处理: 1=已处理 2=已忽略 */
    void handle(Long id, HandleAlertDTO dto);
    /** 统计: 各状态数量 */
    java.util.Map<String, Object> stats();
}