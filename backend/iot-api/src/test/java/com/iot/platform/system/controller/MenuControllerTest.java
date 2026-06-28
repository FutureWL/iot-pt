package com.iot.platform.system.controller;

import com.iot.platform.system.service.MenuService;
import com.iot.platform.system.vo.SysMenuTreeVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class MenuControllerTest extends ControllerTestSupport {

    @Mock
    private MenuService menuService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = buildMockMvc(new MenuController(menuService));
    }

    @Test
    void treeShouldReturnMenuTree() throws Exception {
        SysMenuTreeVO root = new SysMenuTreeVO();
        root.setId(1L);
        root.setMenuName("系统管理");
        root.setPath("/system");

        SysMenuTreeVO child = new SysMenuTreeVO();
        child.setId(2L);
        child.setParentId(1L);
        child.setMenuName("用户管理");
        child.setPath("/system/user");
        root.setChildren(List.of(child));

        when(menuService.tree()).thenReturn(List.of(root));

        mockMvc.perform(get("/system/menu/tree"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].menuName").value("系统管理"))
                .andExpect(jsonPath("$.data[0].children[0].menuName").value("用户管理"))
                .andExpect(jsonPath("$.data[0].children[0].path").value("/system/user"));

        verify(menuService).tree();
    }
}
