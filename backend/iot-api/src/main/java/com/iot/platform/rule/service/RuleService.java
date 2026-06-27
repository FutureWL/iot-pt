package com.iot.platform.rule.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.iot.platform.rule.dto.IotRuleDTO;
import com.iot.platform.rule.dto.IotRuleQueryDTO;
import com.iot.platform.rule.vo.IotRuleVO;

public interface RuleService {
    IPage<IotRuleVO> page(IotRuleQueryDTO q);
    IotRuleVO detail(Long id);
    void create(IotRuleDTO dto);
    void update(IotRuleDTO dto);
    void delete(Long id);
    void toggle(Long id, Integer status);
}