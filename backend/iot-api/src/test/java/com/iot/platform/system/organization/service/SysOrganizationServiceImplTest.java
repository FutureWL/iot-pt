package com.iot.platform.system.organization.service;

import com.iot.platform.common.BusinessException;
import com.iot.platform.system.organization.entity.SysOrganization;
import com.iot.platform.system.organization.mapper.SysOrganizationMapper;
import com.iot.platform.system.organization.service.impl.SysOrganizationServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.Arrays;
import java.util.Collections;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SysOrganizationServiceImplTest {
    @Mock private SysOrganizationMapper mapper;
    @InjectMocks private SysOrganizationServiceImpl service;

    @Test void treeShouldBuildHierarchy() {
        SysOrganization root = new SysOrganization();
        root.setId(1L); root.setParentId(0L); root.setName("总部"); root.setSort(1);
        SysOrganization child = new SysOrganization();
        child.setId(2L); child.setParentId(1L); child.setName("研发"); child.setSort(1);
        when(mapper.selectList(any())).thenReturn(Arrays.asList(root, child));
        var tree = service.tree();
        assertThat(tree).hasSize(1);
        assertThat(tree.get(0).getName()).isEqualTo("总部");
        assertThat(tree.get(0).getChildren()).hasSize(1);
        assertThat(tree.get(0).getChildren().get(0).getName()).isEqualTo("研发");
    }
    @Test void treeShouldHandleEmpty() {
        when(mapper.selectList(any())).thenReturn(Collections.emptyList());
        assertThat(service.tree()).isEmpty();
    }
    @Test void createShouldRequireName() {
        assertThatThrownBy(() -> service.create(new SysOrganization()))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("组织名称");
        verify(mapper, never()).insert(any(SysOrganization.class));
    }
    @Test void createShouldValidateParent() {
        SysOrganization o = new SysOrganization();
        o.setName("test"); o.setParentId(99L);
        when(mapper.selectById(99L)).thenReturn(null);
        assertThatThrownBy(() -> service.create(o))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("父组织不存在");
    }
    @Test void createShouldDefaultParentToZero() {
        SysOrganization o = new SysOrganization();
        o.setName("top");
        service.create(o);
        assertThat(o.getParentId()).isEqualTo(0L);
        verify(mapper, times(1)).insert(o);
    }
    @Test void updateShouldRejectSelfAsParent() {
        SysOrganization exist = new SysOrganization();
        exist.setId(5L);
        when(mapper.selectById(5L)).thenReturn(exist);
        SysOrganization dto = new SysOrganization();
        dto.setId(5L); dto.setParentId(5L);
        assertThatThrownBy(() -> service.update(dto))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("父组织不能是自己");
    }
    @Test void deleteShouldCascadeChildren() {
        SysOrganization exist = new SysOrganization();
        exist.setId(10L);
        SysOrganization child = new SysOrganization();
        child.setId(11L); child.setParentId(10L);
        when(mapper.selectById(10L)).thenReturn(exist);
        // 第一次 selectList 返回 child(11);之后都返回空(防递归循环)
        when(mapper.selectList(any()))
            .thenReturn(Collections.singletonList(child))
            .thenReturn(Collections.emptyList());
        service.delete(10L);
        verify(mapper, times(1)).deleteById(10L);
        verify(mapper, times(1)).deleteById(11L);
    }
}