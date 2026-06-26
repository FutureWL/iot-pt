package com.iot.platform.product.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.iot.platform.product.entity.IotProduct;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface IotProductMapper extends BaseMapper<IotProduct> {
}