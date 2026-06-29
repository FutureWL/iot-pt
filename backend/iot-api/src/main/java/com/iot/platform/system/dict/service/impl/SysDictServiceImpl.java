package com.iot.platform.system.dict.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.iot.platform.common.BusinessException;
import com.iot.platform.system.dict.entity.SysDictItem;
import com.iot.platform.system.dict.entity.SysDictType;
import com.iot.platform.system.dict.mapper.SysDictItemMapper;
import com.iot.platform.system.dict.mapper.SysDictTypeMapper;
import com.iot.platform.system.dict.service.SysDictService;
import com.iot.platform.system.dict.vo.SysDictTypeVO;
import com.iot.platform.system.dict.vo.SysDictVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service @RequiredArgsConstructor
public class SysDictServiceImpl implements SysDictService {
    private final SysDictTypeMapper typeMapper;
    private final SysDictItemMapper itemMapper;

    @Override public Map<String, Object> pageTypes(Map<String, Object> params) {
        Integer current = safeInt(params.get("current"), 1);
        Integer size = safeInt(params.get("size"), 10);
        String keyword = (String) params.get("keyword");
        Integer status = safeInt(params.get("status"), null);

        Page<SysDictType> p = new Page<>(current, size);
        LambdaQueryWrapper<SysDictType> w = new LambdaQueryWrapper<>();
        if (status != null) w.eq(SysDictType::getStatus, status);
        if (StrUtil.isNotBlank(keyword)) {
            w.and(q -> q.like(SysDictType::getType, keyword).or().like(SysDictType::getTypeName, keyword));
        }
        w.orderByAsc(SysDictType::getId);
        Page<SysDictType> res = typeMapper.selectPage(p, w);
        return pageToMap(res, this::typeToVO);
    }

    @Override public Map<String, Object> pageItems(Map<String, Object> params) {
        Integer current = safeInt(params.get("current"), 1);
        Integer size = safeInt(params.get("size"), 10);
        String type = (String) params.get("type");
        String keyword = (String) params.get("keyword");
        Integer status = safeInt(params.get("status"), null);

        Page<SysDictItem> p = new Page<>(current, size);
        LambdaQueryWrapper<SysDictItem> w = new LambdaQueryWrapper<>();
        if (StrUtil.isNotBlank(type)) w.eq(SysDictItem::getType, type);
        if (status != null) w.eq(SysDictItem::getStatus, status);
        if (StrUtil.isNotBlank(keyword)) {
            w.and(q -> q.like(SysDictItem::getCode, keyword).or().like(SysDictItem::getLabel, keyword));
        }
        w.orderByAsc(SysDictItem::getSort).orderByAsc(SysDictItem::getId);
        Page<SysDictItem> res = itemMapper.selectPage(p, w);
        return pageToMap(res, this::itemToVO);
    }

    @Override public Long createType(SysDictType dto) {
        if (dto == null || StrUtil.isBlank(dto.getType())) throw new BusinessException("字典类型编码不能为空");
        if (StrUtil.isBlank(dto.getTypeName())) throw new BusinessException("字典类型名称不能为空");
        // 唯一性检查
        Long cnt = typeMapper.selectCount(new LambdaQueryWrapper<SysDictType>().eq(SysDictType::getType, dto.getType()));
        if (cnt > 0) throw new BusinessException("字典类型已存在: " + dto.getType());
        if (dto.getStatus() == null) dto.setStatus(1);
        typeMapper.insert(dto);
        return dto.getId();
    }

    @Override public Long createItem(SysDictItem dto) {
        if (dto == null || StrUtil.isBlank(dto.getType())) throw new BusinessException("字典项 type 不能为空");
        if (StrUtil.isBlank(dto.getCode())) throw new BusinessException("字典项编码不能为空");
        if (StrUtil.isBlank(dto.getLabel())) throw new BusinessException("字典项显示名不能为空");
        if (StrUtil.isBlank(dto.getValue())) throw new BusinessException("字典项值不能为空");
        // 关联 type 必须存在
        Long typeCnt = typeMapper.selectCount(new LambdaQueryWrapper<SysDictType>().eq(SysDictType::getType, dto.getType()));
        if (typeCnt == 0) throw new BusinessException("字典类型不存在: " + dto.getType());
        // type+code 唯一
        Long cnt = itemMapper.selectCount(new LambdaQueryWrapper<SysDictItem>()
            .eq(SysDictItem::getType, dto.getType()).eq(SysDictItem::getCode, dto.getCode()));
        if (cnt > 0) throw new BusinessException("字典项已存在: " + dto.getType() + " / " + dto.getCode());
        if (dto.getStatus() == null) dto.setStatus(1);
        if (dto.getSort() == null) dto.setSort(0);
        itemMapper.insert(dto);
        return dto.getId();
    }

    @Override public void updateType(SysDictType dto) {
        if (dto == null || dto.getId() == null) throw new BusinessException("id 不能为空");
        SysDictType exist = typeMapper.selectById(dto.getId());
        if (exist == null) throw new BusinessException("字典类型不存在: " + dto.getId());
        // type 不能改
        dto.setType(null);
        dto.setCreatedAt(null);
        typeMapper.updateById(dto);
    }

    @Override public void updateItem(SysDictItem dto) {
        if (dto == null || dto.getId() == null) throw new BusinessException("id 不能为空");
        SysDictItem exist = itemMapper.selectById(dto.getId());
        if (exist == null) throw new BusinessException("字典项不存在: " + dto.getId());
        dto.setType(null);  // type/code 不允许改
        dto.setCode(null);
        dto.setCreatedAt(null);
        itemMapper.updateById(dto);
    }

    @Override @Transactional
    public void deleteType(Long id) {
        SysDictType t = typeMapper.selectById(id);
        if (t == null) throw new BusinessException("字典类型不存在: " + id);
        // cascade 删 item
        itemMapper.delete(new LambdaQueryWrapper<SysDictItem>().eq(SysDictItem::getType, t.getType()));
        typeMapper.deleteById(id);
    }

    @Override public void deleteItem(Long id) {
        SysDictItem i = itemMapper.selectById(id);
        if (i == null) throw new BusinessException("字典项不存在: " + id);
        itemMapper.deleteById(id);
    }

    // ========== helpers ==========
    private SysDictTypeVO typeToVO(SysDictType t) {
        SysDictTypeVO vo = new SysDictTypeVO();
        BeanUtil.copyProperties(t, vo);
        return vo;
    }
    private SysDictVO itemToVO(SysDictItem i) {
        SysDictVO vo = new SysDictVO();
        BeanUtil.copyProperties(i, vo);
        return vo;
    }
    private static Integer safeInt(Object v, Integer defaultVal) {
        if (v == null) return defaultVal;
        if (v instanceof Integer) return (Integer) v;
        if (v instanceof Number) return ((Number) v).intValue();
        if (v instanceof String) {
            try { return Integer.parseInt(((String) v).trim()); } catch (NumberFormatException e) { return defaultVal; }
        }
        return defaultVal;
    }
    private <T, V> Map<String, Object> pageToMap(Page<T> page, java.util.function.Function<T, V> mapper) {
        Map<String, Object> map = new HashMap<>();
        List<V> records = page.getRecords().stream().map(mapper).collect(Collectors.toList());
        map.put("records", records);
        map.put("total", page.getTotal());
        map.put("size", page.getSize());
        map.put("current", page.getCurrent());
        map.put("pages", page.getPages());
        return map;
    }
}