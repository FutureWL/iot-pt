package com.iot.platform.system.organization.controller;

import com.iot.platform.system.organization.service.SysOrganizationService;
import com.iot.platform.system.organization.vo.SysOrganizationVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class SysOrganizationControllerTest extends ControllerTestSupport {
    @Mock private SysOrganizationService service;
    private MockMvc mockMvc;
    @BeforeEach void setUp() { mockMvc = buildMockMvc(new SysOrganizationController(service)); }

    @Test
    void treeShouldReturnHierarchicalList() throws Exception {
        SysOrganizationVO root = new SysOrganizationVO();
        root.setId(1L); root.setParentId(0L); root.setName("国网总部"); root.setSort(1);
        root.setPath("/1");

        SysOrganizationVO child = new SysOrganizationVO();
        child.setId(2L); child.setParentId(1L); child.setName("华东分部"); child.setSort(2);
        child.setPath("/1/2");

        root.setChildren(List.of(child));

        when(service.tree()).thenReturn(List.of(root));

        mockMvc.perform(get("/system/organization/tree"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].name").value("国网总部"))
                .andExpect(jsonPath("$.data[0].children[0].name").value("华东分部"))
                .andExpect(jsonPath("$.data[0].children[0].parentId").value(1));
    }
}
