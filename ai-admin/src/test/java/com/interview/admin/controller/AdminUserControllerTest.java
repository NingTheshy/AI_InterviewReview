package com.interview.admin.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.interview.admin.dto.UserManagementResponse;
import com.interview.admin.dto.UserRoleRequest;
import com.interview.admin.dto.UserStatusRequest;
import com.interview.admin.service.AdminService;
import com.interview.common.exception.BusinessException;
import com.interview.common.exception.GlobalExceptionHandler;
import com.interview.common.result.PageResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * AdminUserController 功能测试
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AdminUserController 功能测试")
class AdminUserControllerTest {

    private MockMvc mockMvc;
    private ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private AdminService adminService;

    @InjectMocks
    private AdminUserController adminUserController;

    private static final Long TEST_USER_ID = 1L;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(adminUserController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(TEST_USER_ID, "admin",
                        List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
    }

    // ==================== 获取用户列表测试 ====================

    @Nested
    @DisplayName("GET /admin/users 测试")
    class ListUsersTest {

        @Test
        @DisplayName("获取用户列表成功")
        void listUsers_Success() throws Exception {
            UserManagementResponse user = UserManagementResponse.builder()
                    .id(1L)
                    .username("testuser")
                    .email("test@example.com")
                    .build();
            when(adminService.listUsers(anyInt(), anyInt(), any(), any()))
                    .thenReturn(new PageResult<>(List.of(user), 1, 1, 10));

            mockMvc.perform(get("/admin/users")
                            .param("page", "1")
                            .param("size", "10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.records.length()").value(1));
        }

        @Test
        @DisplayName("获取用户列表 - 空列表")
        void listUsers_Empty() throws Exception {
            when(adminService.listUsers(anyInt(), anyInt(), any(), any()))
                    .thenReturn(new PageResult<>(List.of(), 0, 1, 10));

            mockMvc.perform(get("/admin/users")
                            .param("page", "1")
                            .param("size", "10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.records.length()").value(0));
        }

        @Test
        @DisplayName("获取用户列表 - 关键词搜索")
        void listUsers_WithKeyword() throws Exception {
            when(adminService.listUsers(anyInt(), anyInt(), anyString(), any()))
                    .thenReturn(new PageResult<>(List.of(), 0, 1, 10));

            mockMvc.perform(get("/admin/users")
                            .param("page", "1")
                            .param("size", "10")
                            .param("keyword", "test"))
                    .andExpect(status().isOk());
        }
    }

    // ==================== 获取用户详情测试 ====================

    @Nested
    @DisplayName("GET /admin/users/{id} 测试")
    class GetUserDetailTest {

        @Test
        @DisplayName("获取用户详情成功")
        void getUserDetail_Success() throws Exception {
            UserManagementResponse user = UserManagementResponse.builder()
                    .id(TEST_USER_ID)
                    .username("testuser")
                    .email("test@example.com")
                    .build();
            when(adminService.getUserDetail(TEST_USER_ID)).thenReturn(user);

            mockMvc.perform(get("/admin/users/{id}", TEST_USER_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.username").value("testuser"));
        }

        @Test
        @DisplayName("获取用户详情 - 用户不存在")
        void getUserDetail_NotFound() throws Exception {
            when(adminService.getUserDetail(999L))
                    .thenThrow(new BusinessException(404, "资源不存在"));

            mockMvc.perform(get("/admin/users/{id}", 999L))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value(404));
        }
    }

    // ==================== 更新用户状态测试 ====================

    @Nested
    @DisplayName("PUT /admin/users/{id}/status 测试")
    class UpdateUserStatusTest {

        @Test
        @DisplayName("更新用户状态成功")
        void updateUserStatus_Success() throws Exception {
            doNothing().when(adminService).updateUserStatus(anyLong(), anyInt());

            UserStatusRequest request = new UserStatusRequest();
            request.setStatus(0);

            mockMvc.perform(put("/admin/users/{id}/status", TEST_USER_ID)
                            .contentType("application/json")
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));
        }

        @Test
        @DisplayName("更新用户状态 - 用户不存在")
        void updateUserStatus_NotFound() throws Exception {
            doThrow(new BusinessException(404, "资源不存在"))
                    .when(adminService).updateUserStatus(eq(999L), anyInt());

            UserStatusRequest request = new UserStatusRequest();
            request.setStatus(0);

            mockMvc.perform(put("/admin/users/{id}/status", 999L)
                            .contentType("application/json")
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value(404));
        }
    }

    // ==================== 更新用户角色测试 ====================

    @Nested
    @DisplayName("PUT /admin/users/{id}/role 测试")
    class UpdateUserRoleTest {

        @Test
        @DisplayName("更新用户角色成功")
        void updateUserRole_Success() throws Exception {
            doNothing().when(adminService).updateUserRole(anyLong(), anyInt());

            UserRoleRequest request = new UserRoleRequest();
            request.setRole(1);

            mockMvc.perform(put("/admin/users/{id}/role", TEST_USER_ID)
                            .contentType("application/json")
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));
        }

        @Test
        @DisplayName("更新用户角色 - 用户不存在")
        void updateUserRole_NotFound() throws Exception {
            doThrow(new BusinessException(404, "资源不存在"))
                    .when(adminService).updateUserRole(eq(999L), anyInt());

            UserRoleRequest request = new UserRoleRequest();
            request.setRole(1);

            mockMvc.perform(put("/admin/users/{id}/role", 999L)
                            .contentType("application/json")
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value(404));
        }
    }
}
