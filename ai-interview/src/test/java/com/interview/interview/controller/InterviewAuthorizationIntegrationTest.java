package com.interview.interview.controller;

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
import com.interview.interview.service.InterviewService;
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

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 使用真实 SecurityFilterChain、JWT 过滤器和方法安全代理验证 RBAC。
 *
 * <p>该测试与 standalone MockMvc 测试不同，能够在权限注解失效或被删除时直接失败。</p>
 */
@WebMvcTest(controllers = InterviewController.class)
@ImportAutoConfiguration(exclude = UserDetailsServiceAutoConfiguration.class)
@ContextConfiguration(classes = {
        InterviewController.class,
        SecurityConfig.class,
        JwtAuthenticationFilter.class
})
class InterviewAuthorizationIntegrationTest {

    private static final String TOKEN = "integration-token";
    private static final Long USER_ID = 7L;

    @Autowired private MockMvc mockMvc;

    @MockitoBean private InterviewService interviewService;
    @MockitoBean private JwtUtil jwtUtil;
    @MockitoBean private RedisUtil redisUtil;
    @MockitoBean private UserMapper userMapper;
    @MockitoBean private RbacService rbacService;

    private User activeUser;

    @BeforeEach
    void setUp() {
        Claims claims = mock(Claims.class);
        when(claims.getId()).thenReturn("integration-jti");
        when(jwtUtil.validateToken(TOKEN)).thenReturn(true);
        when(jwtUtil.parseToken(TOKEN)).thenReturn(claims);
        when(jwtUtil.getUserId(TOKEN)).thenReturn(USER_ID);
        when(jwtUtil.getUsername(TOKEN)).thenReturn("security-test-user");
        when(redisUtil.hasKey(anyString())).thenReturn(false);

        activeUser = new User();
        activeUser.setId(USER_ID);
        activeUser.setStatus(UserStatus.ACTIVE.getCode());
        when(userMapper.selectById(USER_ID)).thenReturn(activeUser);
        when(interviewService.list(org.mockito.ArgumentMatchers.eq(USER_ID), anyInt(), anyInt(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()))
                .thenReturn(new PageResult<>(List.of(), 0, 1, 10));
    }

    @Test
    @DisplayName("未登录访问受保护接口返回 401")
    void unauthenticatedRequestIsRejected() throws Exception {
        mockMvc.perform(get("/interviews"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    @DisplayName("已登录但缺少权限返回 403")
    void missingAuthorityIsRejected() throws Exception {
        when(rbacService.getPermissionCodes(USER_ID)).thenReturn(Set.of());

        mockMvc.perform(authorizedGet())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    @DisplayName("拥有具体权限码时允许访问")
    void matchingAuthorityIsAllowed() throws Exception {
        when(rbacService.getPermissionCodes(USER_ID)).thenReturn(Set.of(PermissionCodes.INTERVIEW_READ));

        mockMvc.perform(authorizedGet())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(interviewService).list(org.mockito.ArgumentMatchers.eq(USER_ID), anyInt(), anyInt(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("数据库撤权后下一次请求立即返回 403")
    void revokedAuthorityTakesEffectOnNextRequest() throws Exception {
        when(rbacService.getPermissionCodes(USER_ID))
                .thenReturn(Set.of(PermissionCodes.INTERVIEW_READ), Set.of());

        mockMvc.perform(authorizedGet()).andExpect(status().isOk());
        mockMvc.perform(authorizedGet()).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("禁用账号携带旧 JWT 仍返回 403")
    void disabledUserIsRejected() throws Exception {
        activeUser.setStatus(UserStatus.DISABLED.getCode());

        mockMvc.perform(authorizedGet())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder authorizedGet() {
        return get("/interviews").header("Authorization", "Bearer " + TOKEN);
    }
}
