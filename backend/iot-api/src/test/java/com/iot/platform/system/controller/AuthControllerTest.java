package com.iot.platform.system.controller;

import com.iot.platform.system.dto.LoginDTO;
import com.iot.platform.system.service.AuthService;
import com.iot.platform.system.vo.UserInfoVO;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest extends ControllerTestSupport {

    @Mock
    private AuthService authService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = buildMockMvc(new AuthController(authService));
    }

    @Test
    void loginShouldReturnUserInfo() throws Exception {
        UserInfoVO userInfo = UserInfoVO.builder()
                .token("jwt-token")
                .userId(1L)
                .username("admin")
                .nickname("Administrator")
                .tenantId(100L)
                .tenantCode("tenant-a")
                .tenantName("Tenant A")
                .roles(List.of("ADMIN"))
                .permissions(List.of("system:user:page"))
                .build();
        when(authService.login(any(LoginDTO.class))).thenReturn(userInfo);

        LoginDTO dto = new LoginDTO();
        dto.setTenantCode("tenant-a");
        dto.setUsername("admin");
        dto.setPassword("secret");

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("操作成功"))
                .andExpect(jsonPath("$.data.token").value("jwt-token"))
                .andExpect(jsonPath("$.data.username").value("admin"))
                .andExpect(jsonPath("$.data.roles[0]").value("ADMIN"));

        ArgumentCaptor<LoginDTO> captor = ArgumentCaptor.forClass(LoginDTO.class);
        verify(authService).login(captor.capture());
        assertThat(captor.getValue().getTenantCode()).isEqualTo("tenant-a");
        assertThat(captor.getValue().getUsername()).isEqualTo("admin");
        assertThat(captor.getValue().getPassword()).isEqualTo("secret");
    }

    @Test
    void loginShouldReturnValidationErrorWhenUsernameBlank() throws Exception {
        LoginDTO dto = new LoginDTO();
        dto.setTenantCode("tenant-a");
        dto.setUsername(" ");
        dto.setPassword("secret");

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("用户名不能为空"));

        verifyNoInteractions(authService);
    }

    @Test
    void infoShouldReturnCurrentUser() throws Exception {
        UserInfoVO userInfo = UserInfoVO.builder()
                .userId(2L)
                .username("viewer")
                .nickname("Viewer")
                .roles(List.of("VIEWER"))
                .permissions(List.of("system:menu:tree"))
                .build();
        when(authService.currentUser()).thenReturn(userInfo);

        mockMvc.perform(get("/auth/info"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.userId").value(2))
                .andExpect(jsonPath("$.data.username").value("viewer"));

        verify(authService).currentUser();
    }

    @Test
    void logoutShouldInvokeService() throws Exception {
        doNothing().when(authService).logout();

        mockMvc.perform(post("/auth/logout"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("操作成功"));

        verify(authService).logout();
    }
}
