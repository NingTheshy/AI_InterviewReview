package com.interview.admin.controller;

import com.interview.admin.service.AdminService;
import com.interview.auth.config.JwtAuthenticationFilter;
import com.interview.auth.config.SecurityConfig;
import com.interview.auth.entity.User;
import com.interview.auth.mapper.UserMapper;
import com.interview.auth.service.RbacService;
import com.interview.common.constant.PermissionCodes;
import com.interview.common.constant.UserStatus;
import com.interview.common.result.PageResult;
import com.interview.common.utils.JwtUtil;
import com.interview.common.utils.RedisUtil;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 使用真实安全过滤链验证管理员接口只接受具体权限码。 */
@WebMvcTest(controllers = AdminUserController.class)
@ImportAutoConfiguration(exclude = UserDetailsServiceAutoConfiguration.class)
@ContextConfiguration(classes = {
        AdminUserController.class,
        SecurityConfig.class,
        JwtAuthenticationFilter.class
})
class AdminAuthorizationIntegrationTest {

    private static final String TOKEN = "admin-integration-token";
    private static final Long USER_ID = 9L;

    @Autowired private MockMvc mockMvc;

    @MockitoBean private AdminService adminService;
    @MockitoBean private JwtUtil jwtUtil;
    @MockitoBean private RedisUtil redisUtil;
    @MockitoBean private UserMapper userMapper;
    @MockitoBean private RbacService rbacService;

    @BeforeEach
    void setUp() {
        Claims claims = mock(Claims.class);
        when(claims.getId()).thenReturn("admin-integration-jti");
        when(jwtUtil.validateToken(TOKEN)).thenReturn(true);
        when(jwtUtil.parseToken(TOKEN)).thenReturn(claims);
        when(jwtUtil.getUserId(TOKEN)).thenReturn(USER_ID);
        when(jwtUtil.getUsername(TOKEN)).thenReturn("permission-manager");
        when(redisUtil.hasKey(anyString())).thenReturn(false);

        User activeUser = new User();
        activeUser.setId(USER_ID);
        activeUser.setStatus(UserStatus.ACTIVE.getCode());
        when(userMapper.selectById(USER_ID)).thenReturn(activeUser);
        when(adminService.listUsers(anyInt(), anyInt(), any(), any()))
                .thenReturn(new PageResult<>(List.of(), 0, 1, 10));
    }

    @Test
    @DisplayName("未登录访问管理员接口返回 401")
    void unauthenticatedRequestIsRejected() throws Exception {
        mockMvc.perform(get("/admin/users"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    @DisplayName("仅有管理员角色名称不能绕过具体权限码")
    void adminRoleNameDoesNotBypassPermission() throws Exception {
        when(rbacService.getPermissionCodes(USER_ID)).thenReturn(Set.of("ROLE_ADMIN"));

        mockMvc.perform(authorizedGet())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    @DisplayName("拥有其他管理权限仍不能查看用户")
    void unrelatedAuthorityIsRejected() throws Exception {
        when(rbacService.getPermissionCodes(USER_ID))
                .thenReturn(Set.of(PermissionCodes.USER_STATUS_UPDATE));

        mockMvc.perform(authorizedGet())
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("拥有 user:read 权限时允许查看用户")
    void matchingAuthorityIsAllowed() throws Exception {
        when(rbacService.getPermissionCodes(USER_ID)).thenReturn(Set.of(PermissionCodes.USER_READ));

        mockMvc.perform(authorizedGet())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(adminService).listUsers(anyInt(), anyInt(), any(), any());
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder authorizedGet() {
        return get("/admin/users").header("Authorization", "Bearer " + TOKEN);
    }
}
