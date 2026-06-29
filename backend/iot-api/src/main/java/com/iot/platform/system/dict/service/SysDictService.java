package com.iot.platform.system.dict.service;
import com.iot.platform.system.dict.entity.SysDictItem;
import com.iot.platform.system.dict.entity.SysDictType;
import com.iot.platform.system.dict.vo.SysDictTypeVO;
import com.iot.platform.system.dict.vo.SysDictVO;
import java.util.List;
import java.util.Map;
public interface SysDictService {
    Map<String, Object> pageTypes(Map<String, Object> params);
    Map<String, Object> pageItems(Map<String, Object> params);
    Long createType(SysDictType dto);
    Long createItem(SysDictItem dto);
    void updateType(SysDictType dto);
    void updateItem(SysDictItem dto);
    void deleteType(Long id);
    void deleteItem(Long id);
}