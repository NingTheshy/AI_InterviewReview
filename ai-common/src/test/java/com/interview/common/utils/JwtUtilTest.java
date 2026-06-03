package com.interview.common.utils;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("JwtUtil 工具类测试")
class JwtUtilTest {

    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(jwtUtil, "secret", "a]veryLongSecretKeyForHmacSha256Algorithm!");
        ReflectionTestUtils.setField(jwtUtil, "expiration", 3600L);
    }

    @Test
    @DisplayName("generateToken - 生成有效 Token")
    void generateToken_validInput_returnsToken() {
        String token = jwtUtil.generateToken(1001L, "testuser");
        assertNotNull(token);
        assertFalse(token.isEmpty());
    }

    @Test
    @DisplayName("parseToken - 解析有效 Token")
    void parseToken_validToken_returnsClaims() {
        String token = jwtUtil.generateToken(1001L, "testuser");
        Claims claims = jwtUtil.parseToken(token);
        assertNotNull(claims);
        assertEquals("1001", claims.getSubject());
        assertEquals("testuser", claims.get("username", String.class));
    }

    @Test
    @DisplayName("getUserId - 获取用户ID")
    void getUserId_validToken_returnsUserId() {
        String token = jwtUtil.generateToken(1001L, "testuser");
        Long userId = jwtUtil.getUserId(token);
        assertEquals(1001L, userId);
    }

    @Test
    @DisplayName("getUsername - 获取用户名")
    void getUsername_validToken_returnsUsername() {
        String token = jwtUtil.generateToken(1001L, "testuser");
        String username = jwtUtil.getUsername(token);
        assertEquals("testuser", username);
    }

    @Test
    @DisplayName("isTokenExpired - 未过期返回 false")
    void isTokenExpired_validToken_returnsFalse() {
        String token = jwtUtil.generateToken(1001L, "testuser");
        assertFalse(jwtUtil.isTokenExpired(token));
    }

    @Test
    @DisplayName("validateToken - 有效 Token 返回 true")
    void validateToken_validToken_returnsTrue() {
        String token = jwtUtil.generateToken(1001L, "testuser");
        assertTrue(jwtUtil.validateToken(token));
    }

    @Test
    @DisplayName("validateToken - 无效 Token 返回 false")
    void validateToken_invalidToken_returnsFalse() {
        assertFalse(jwtUtil.validateToken("invalid.token.here"));
    }

    @Test
    @DisplayName("extractJti - 获取 JTI")
    void extractJti_validToken_returnsJti() {
        String token = jwtUtil.generateToken(1001L, "testuser");
        String jti = jwtUtil.extractJti(token);
        assertNotNull(jti);
        assertFalse(jti.isEmpty());
    }

    @Test
    @DisplayName("getExpiration - 获取过期时间")
    void getExpiration_validToken_returnsExpiration() {
        String token = jwtUtil.generateToken(1001L, "testuser");
        long expiration = jwtUtil.getExpiration(token);
        assertTrue(expiration > System.currentTimeMillis());
    }
}
