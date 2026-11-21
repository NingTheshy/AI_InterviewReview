package com.interview.admin.controller;

import com.interview.admin.service.AdminService;
import com.interview.auth.config.JwtAuthenticationFilter;
import com.interview.auth.config.SecurityConfig;
import com.interview.auth.entity.User;
import com.interview.auth.mapper.UserMapper;
import com.interview.auth.service.RbacService;
import com.interview.common.constant.PermissionCodes;
import com.interview.common.constant.UserStatus;
import com.interview.common.utils.JwtUtil;
import com.interview.common.utils.RedisUtil;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Set;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 验证管理员面试读取与处理权限分别生效。 */
@WebMvcTest(controllers = AdminInterviewController.class)
@ImportAutoConfiguration(exclude = UserDetailsServiceAutoConfiguration.class)
@ContextConfiguration(classes = {AdminInterviewController.class, SecurityConfig.class, JwtAuthenticationFilter.class})
class AdminInterviewAuthorizationIntegrationTest {
    @Autowired private MockMvc mockMvc;
    @MockitoBean private AdminService adminService;
    @MockitoBean private JwtUtil jwtUtil;
    @MockitoBean private RedisUtil redisUtil;
    @MockitoBean private UserMapper userMapper;
    @MockitoBean private RbacService rbacService;

    @BeforeEach
    void setUp() {
        Claims claims = mock(Claims.class);
        when(claims.getId()).thenReturn("admin-jti");
        when(jwtUtil.validateToken("admin-token")).thenReturn(true);
        when(jwtUtil.parseToken("admin-token")).thenReturn(claims);
        when(jwtUtil.getUserId("admin-token")).thenReturn(1L);
        when(jwtUtil.getUsername("admin-token")).thenReturn("admin");
        when(redisUtil.hasKey(anyString())).thenReturn(false);
        User user = new User();
        user.setId(1L);
        user.setStatus(UserStatus.ACTIVE.getCode());
        when(userMapper.selectById(1L)).thenReturn(user);
    }

    @Test
    void readPermissionDoesNotAllowCancelOrRetry() throws Exception {
        when(rbacService.getPermissionCodes(1L)).thenReturn(Set.of(PermissionCodes.INTERVIEW_READ_ALL));
        mockMvc.perform(get("/admin/interviews/8").header("Authorization", "Bearer admin-token"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/admin/interviews/8/cancel").header("Authorization", "Bearer admin-token"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/admin/interviews/8/retry").header("Authorization", "Bearer admin-token"))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/admin/interviews/8").header("Authorization", "Bearer admin-token")
                        .contentType("application/json")
                        .content("{\"title\":\"一面\",\"positionTitle\":\"后端工程师\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/admin/interviews/8").header("Authorization", "Bearer admin-token"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/admin/interviews/8/restore").header("Authorization", "Bearer admin-token"))
                .andExpect(status().isForbidden());
        verify(adminService, never()).cancelInterview(anyLong());
        verify(adminService, never()).retryInterview(anyLong());
        verify(adminService, never()).deleteInterview(anyLong());
        verify(adminService, never()).restoreInterview(anyLong());
    }

    @Test
    void managePermissionDoesNotRevealFullRecap() throws Exception {
        when(rbacService.getPermissionCodes(1L)).thenReturn(Set.of(PermissionCodes.INTERVIEW_MANAGE_ALL));
        mockMvc.perform(get("/admin/interviews/8").header("Authorization", "Bearer admin-token"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/admin/interviews/8/cancel").header("Authorization", "Bearer admin-token"))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/admin/interviews/8").header("Authorization", "Bearer admin-token"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/admin/interviews/8/restore").header("Authorization", "Bearer admin-token"))
                .andExpect(status().isOk());
        verify(adminService).cancelInterview(8L);
        verify(adminService).deleteInterview(8L);
        verify(adminService).restoreInterview(8L);
    }
}
