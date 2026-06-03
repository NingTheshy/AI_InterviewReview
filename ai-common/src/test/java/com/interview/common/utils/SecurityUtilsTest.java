package com.interview.common.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SecurityUtils 工具类测试")
class SecurityUtilsTest {

    @Test
    @DisplayName("getCurrentUserId - 已登录返回用户ID")
    void getCurrentUserId_loggedIn_returnsUserId() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(1001L, "testuser"));
        Long userId = SecurityUtils.getCurrentUserId();
        assertEquals(1001L, userId);
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("getCurrentUserId - 未登录返回 null")
    void getCurrentUserId_notLoggedIn_returnsNull() {
        SecurityContextHolder.clearContext();
        assertNull(SecurityUtils.getCurrentUserId());
    }

    @Test
    @DisplayName("getCurrentUsername - 已登录返回用户名")
    void getCurrentUsername_loggedIn_returnsUsername() {
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(1001L, null);
        auth.setDetails("testuser");
        SecurityContextHolder.getContext().setAuthentication(auth);
        String username = SecurityUtils.getCurrentUsername();
        assertEquals("testuser", username);
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("getCurrentUsername - 未登录返回 null")
    void getCurrentUsername_notLoggedIn_returnsNull() {
        SecurityContextHolder.clearContext();
        assertNull(SecurityUtils.getCurrentUsername());
    }
}
