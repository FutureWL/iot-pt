package com.iot.platform.system.dict.service;

import com.iot.platform.common.BusinessException;
import com.iot.platform.system.dict.entity.SysDictItem;
import com.iot.platform.system.dict.entity.SysDictType;
import com.iot.platform.system.dict.mapper.SysDictItemMapper;
import com.iot.platform.system.dict.mapper.SysDictTypeMapper;
import com.iot.platform.system.dict.service.impl.SysDictServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.HashMap;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SysDictServiceImplTest {
    @Mock private SysDictTypeMapper typeMapper;
    @Mock private SysDictItemMapper itemMapper;
    @InjectMocks private SysDictServiceImpl service;

    // ===== 字典类型 =====
    @Test void pageTypesShouldHandleStringParams() {
        Map<String, Object> params = new HashMap<>();
        params.put("current", "1");
        params.put("size", "10");
        params.put("keyword", "device");
        when(typeMapper.selectPage(any(), any())).thenReturn(new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>());
        Map<String, Object> result = service.pageTypes(params);
        assertThat(result).containsKey("records");
    }
    @Test void createTypeShouldRejectBlankCode() {
        assertThatThrownBy(() -> service.createType(new SysDictType()))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("字典类型编码");
        verify(typeMapper, never()).insert(any(SysDictType.class));
    }
    @Test void createTypeShouldRejectDuplicate() {
        SysDictType t = new SysDictType();
        t.setType("dup"); t.setTypeName("X");
        when(typeMapper.selectCount(any())).thenReturn(1L);
        assertThatThrownBy(() -> service.createType(t))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("已存在");
    }
    @Test void createTypeShouldSetDefaultStatus() {
        SysDictType t = new SysDictType();
        t.setType("new_one"); t.setTypeName("新字典");
        when(typeMapper.selectCount(any())).thenReturn(0L);
        service.createType(t);
        assertThat(t.getStatus()).isEqualTo(1);
        verify(typeMapper, times(1)).insert(t);
    }
    @Test void deleteTypeShouldCascadeItems() {
        SysDictType t = new SysDictType();
        t.setId(5L); t.setType("t5");
        when(typeMapper.selectById(5L)).thenReturn(t);
        service.deleteType(5L);
        verify(itemMapper, times(1)).delete(any());
        verify(typeMapper, times(1)).deleteById(5L);
    }

    // ===== 字典项 =====
    @Test void createItemShouldRequireTypeExistence() {
        SysDictItem i = new SysDictItem();
        i.setType("missing"); i.setCode("c"); i.setLabel("l"); i.setValue("v");
        when(typeMapper.selectCount(any())).thenReturn(0L);
        assertThatThrownBy(() -> service.createItem(i))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("字典类型不存在");
    }
    @Test void createItemShouldRejectDuplicate() {
        SysDictItem i = new SysDictItem();
        i.setType("t"); i.setCode("c"); i.setLabel("l"); i.setValue("v");
        when(typeMapper.selectCount(any())).thenReturn(1L);  // type 存在
        when(itemMapper.selectCount(any())).thenReturn(1L);  // item 重复
        assertThatThrownBy(() -> service.createItem(i))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("已存在");
    }
    @Test void createItemShouldLockDefaults() {
        SysDictItem i = new SysDictItem();
        i.setType("t"); i.setCode("c"); i.setLabel("l"); i.setValue("v");
        when(typeMapper.selectCount(any())).thenReturn(1L);
        when(itemMapper.selectCount(any())).thenReturn(0L);
        service.createItem(i);
        assertThat(i.getStatus()).isEqualTo(1);
        assertThat(i.getSort()).isEqualTo(0);
        verify(itemMapper, times(1)).insert(i);
    }
    @Test void updateItemShouldFreezeTypeAndCode() {
        SysDictItem exist = new SysDictItem();
        exist.setId(3L); exist.setType("t"); exist.setCode("c");
        when(itemMapper.selectById(3L)).thenReturn(exist);
        SysDictItem dto = new SysDictItem();
        dto.setId(3L); dto.setType("TAMPERED"); dto.setCode("TAMPERED"); dto.setLabel("new");
        service.updateItem(dto);
        assertThat(dto.getType()).isNull();
        assertThat(dto.getCode()).isNull();
    }
}