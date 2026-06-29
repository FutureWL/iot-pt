package com.iot.platform.system.organization.controller;

import com.iot.platform.system.organization.controller.SysOrganizationController;
import com.iot.platform.system.organization.entity.SysOrganization;
import com.iot.platform.system.organization.service.SysOrganizationService;
import com.iot.platform.system.organization.vo.SysOrganizationVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import java.util.List;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class SysOrganizationControllerTest extends ControllerTestSupport {
    @Mock private SysOrganizationService service;
    private MockMvc mockMvc;
    @BeforeEach void setUp() { mockMvc = buildMockMvc(new SysOrganizationController(service)); }

    @Test void treeShouldReturnHierarchy() throws Exception {
        SysOrganizationVO root = new SysOrganizationVO();
        root.setId(1L); root.setName("总部");
        when(service.tree()).thenReturn(List.of(root));
        mockMvc.perform(get("/system/organization/tree"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].name").value("总部"));
    }
    @Test void createShouldReturnId() throws Exception {
        when(service.create(any())).thenReturn(42L);
        mockMvc.perform(post("/system/organization")
                .contentType("application/json")
                .content("{\"name\":\"研发部\",\"parentId\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(42));
    }
    @Test void updateShouldReturnOk() throws Exception {
        doNothing().when(service).update(any());
        mockMvc.perform(put("/system/organization")
                .contentType("application/json")
                .content("{\"id\":1,\"name\":\"新名称\"}"))
                .andExpect(status().isOk());
        verify(service, times(1)).update(any());
    }
    @Test void deleteShouldReturnOk() throws Exception {
        doNothing().when(service).delete(any());
        mockMvc.perform(delete("/system/organization/3"))
                .andExpect(status().isOk());
        verify(service).delete(3L);
    }
}