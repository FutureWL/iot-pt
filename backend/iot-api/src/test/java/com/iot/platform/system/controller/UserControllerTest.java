package com.iot.platform.system.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.iot.platform.system.dto.ResetPasswordDTO;
import com.iot.platform.system.dto.UserDTO;
import com.iot.platform.system.dto.UserQueryDTO;
import com.iot.platform.system.service.UserService;
import com.iot.platform.system.vo.SysUserVO;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class UserControllerTest extends ControllerTestSupport {

    @Mock
    private UserService userService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = buildMockMvc(new UserController(userService));
    }

    @Test
    void pageShouldReturnPagedUsers() throws Exception {
        SysUserVO user = new SysUserVO();
        user.setId(10L);
        user.setUsername("alice");
        user.setNickname("Alice");
        user.setStatus(1);

        Page<SysUserVO> page = new Page<>(2, 5);
        page.setTotal(11);
        page.setRecords(List.of(user));
        when(userService.page(any(UserQueryDTO.class))).thenReturn(page);

        mockMvc.perform(get("/system/user/page")
                        .param("pageNum", "2")
                        .param("pageSize", "5")
                        .param("keyword", "alice")
                        .param("status", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.current").value(2))
                .andExpect(jsonPath("$.data.size").value(5))
                .andExpect(jsonPath("$.data.total").value(11))
                .andExpect(jsonPath("$.data.records[0].username").value("alice"));

        ArgumentCaptor<UserQueryDTO> captor = ArgumentCaptor.forClass(UserQueryDTO.class);
        verify(userService).page(captor.capture());
        assertThat(captor.getValue().getPageNum()).isEqualTo(2);
        assertThat(captor.getValue().getPageSize()).isEqualTo(5);
        assertThat(captor.getValue().getKeyword()).isEqualTo("alice");
        assertThat(captor.getValue().getStatus()).isEqualTo(1);
    }

    @Test
    void detailShouldReturnUser() throws Exception {
        SysUserVO user = new SysUserVO();
        user.setId(7L);
        user.setUsername("bob");
        user.setNickname("Bob");
        when(userService.detail(7L)).thenReturn(user);

        mockMvc.perform(get("/system/user/7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(7))
                .andExpect(jsonPath("$.data.username").value("bob"));

        verify(userService).detail(7L);
    }

    @Test
    void createShouldInvokeService() throws Exception {
        doNothing().when(userService).create(any(UserDTO.class));

        UserDTO dto = new UserDTO();
        dto.setUsername("charlie");
        dto.setPassword("secret123");
        dto.setNickname("Charlie");
        dto.setStatus(1);

        mockMvc.perform(post("/system/user")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("操作成功"));

        ArgumentCaptor<UserDTO> captor = ArgumentCaptor.forClass(UserDTO.class);
        verify(userService).create(captor.capture());
        assertThat(captor.getValue().getUsername()).isEqualTo("charlie");
        assertThat(captor.getValue().getPassword()).isEqualTo("secret123");
    }

    @Test
    void createShouldReturnValidationErrorWhenUsernameBlank() throws Exception {
        UserDTO dto = new UserDTO();
        dto.setUsername(" ");
        dto.setPassword("secret123");

        mockMvc.perform(post("/system/user")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message", Matchers.containsString("用户名不能为空")));

        verifyNoInteractions(userService);
    }

    @Test
    void updateShouldInvokeService() throws Exception {
        doNothing().when(userService).update(any(UserDTO.class));

        UserDTO dto = new UserDTO();
        dto.setId(8L);
        dto.setUsername("david");
        dto.setNickname("David");
        dto.setStatus(0);

        mockMvc.perform(put("/system/user")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        ArgumentCaptor<UserDTO> captor = ArgumentCaptor.forClass(UserDTO.class);
        verify(userService).update(captor.capture());
        assertThat(captor.getValue().getId()).isEqualTo(8L);
        assertThat(captor.getValue().getStatus()).isEqualTo(0);
    }

    @Test
    void deleteShouldInvokeService() throws Exception {
        doNothing().when(userService).delete(9L);

        mockMvc.perform(delete("/system/user/9"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(userService).delete(9L);
    }

    @Test
    void resetPasswordShouldInvokeService() throws Exception {
        doNothing().when(userService).resetPassword(any(Long.class), any(ResetPasswordDTO.class));

        ResetPasswordDTO dto = new ResetPasswordDTO();
        dto.setNewPassword("newSecret");

        mockMvc.perform(post("/system/user/6/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        ArgumentCaptor<ResetPasswordDTO> captor = ArgumentCaptor.forClass(ResetPasswordDTO.class);
        verify(userService).resetPassword(org.mockito.ArgumentMatchers.eq(6L), captor.capture());
        assertThat(captor.getValue().getNewPassword()).isEqualTo("newSecret");
    }

    @Test
    void toggleStatusShouldInvokeService() throws Exception {
        doNothing().when(userService).toggleStatus(5L, 0);

        mockMvc.perform(put("/system/user/5/status/0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(userService).toggleStatus(5L, 0);
    }
}
