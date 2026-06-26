package com.iot.platform.product.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.iot.platform.product.dto.IotProductDTO;
import com.iot.platform.product.dto.IotProductQueryDTO;
import com.iot.platform.product.vo.IotProductVO;

import java.util.List;

public interface IotProductService {
    IPage<IotProductVO> page(IotProductQueryDTO q);
    List<IotProductVO> all();
    IotProductVO detail(Long id);
    void create(IotProductDTO dto);
    void update(IotProductDTO dto);
    void delete(Long id);

    /** 提供一个默认物模型(空属性/空事件/空服务) */
    String defaultThingModel();
}