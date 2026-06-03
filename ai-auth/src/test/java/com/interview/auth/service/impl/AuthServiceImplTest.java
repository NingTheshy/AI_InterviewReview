package com.interview.auth.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.interview.auth.dto.*;
import com.interview.auth.entity.User;
import com.interview.auth.mapper.UserMapper;
import com.interview.common.constant.ErrorCode;
import com.interview.common.constant.UserStatus;
import com.interview.common.exception.BusinessException;
import com.interview.common.utils.JwtUtil;
import com.interview.common.utils.RedisUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * AuthServiceImpl 单元测试
 * <p>
 * 使用 Mockito 模拟依赖，测试各个方法的业务逻辑。
 * </p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AuthService 单元测试")
class AuthServiceImplTest {

    @Mock
    private UserMapper userMapper;

    @Mock
    private RedisUtil redisUtil;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtUtil jwtUtil;

    @InjectMocks
    private AuthServiceImpl authService;

    private User testUser;
    private static final String TEST_EMAIL = "test@example.com";
    private static final String TEST_USERNAME = "testuser";
    private static final String TEST_PASSWORD = "password123";
    private static final String TEST_CODE = "123456";
    private static final Long TEST_USER_ID = 1L;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId(TEST_USER_ID);
        testUser.setEmail(TEST_EMAIL);
        testUser.setUsername(TEST_USERNAME);
        testUser.setPassword(TEST_PASSWORD);
        testUser.setNickname(TEST_USERNAME);
        testUser.setRole(0);
        testUser.setStatus(UserStatus.ACTIVE.getCode());
        testUser.setLoginCount(0);
        testUser.setLastLoginAt(LocalDateTime.now());
    }

    // ==================== sendVerifyCode 测试 ====================

    @Nested
    @DisplayName("sendVerifyCode 方法测试")
    class SendVerifyCodeTest {

        @Test
        @DisplayName("发送验证码成功")
        void sendVerifyCode_Success() {
            // Arrange
            when(redisUtil.hasKey(anyString())).thenReturn(false);

            // Act
            authService.sendVerifyCode(TEST_EMAIL);

            // Assert
            verify(redisUtil, times(2)).set(anyString(), anyString(), anyLong(), any());
        }

        @Test
        @DisplayName("发送验证码 - 60秒内重复发送")
        void sendVerifyCode_RateLimit() {
            // Arrange
            when(redisUtil.hasKey(anyString())).thenReturn(true);

            // Act & Assert
            BusinessException exception = assertThrows(BusinessException.class,
                    () -> authService.sendVerifyCode(TEST_EMAIL));

            assertEquals(ErrorCode.VERIFY_CODE_FREQUENT.getCode(), exception.getCode());
        }
    }

    // ==================== register 测试 ====================

    @Nested
    @DisplayName("register 方法测试")
    class RegisterTest {

        @Test
        @DisplayName("注册成功")
        void register_Success() {
            // Arrange
            RegisterRequest request = new RegisterRequest();
            request.setEmail(TEST_EMAIL);
            request.setUsername(TEST_USERNAME);
            request.setPassword(TEST_PASSWORD);
            request.setCode(TEST_CODE);

            when(redisUtil.get(anyString())).thenReturn(TEST_CODE);
            when(userMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
            when(passwordEncoder.encode(anyString())).thenReturn("encoded_password");
            when(userMapper.insert(any(User.class))).thenReturn(1);

            // Act
            authService.register(request);

            // Assert
            verify(userMapper).insert(any(User.class));
        }

        @Test
        @DisplayName("注册失败 - 验证码错误")
        void register_VerifyCodeError() {
            // Arrange
            RegisterRequest request = new RegisterRequest();
            request.setEmail(TEST_EMAIL);
            request.setUsername(TEST_USERNAME);
            request.setPassword(TEST_PASSWORD);
            request.setCode("wrong_code");

            when(redisUtil.get(anyString())).thenReturn(TEST_CODE);

            // Act & Assert
            BusinessException exception = assertThrows(BusinessException.class,
                    () -> authService.register(request));

            assertEquals(ErrorCode.VERIFY_CODE_ERROR.getCode(), exception.getCode());
        }

        @Test
        @DisplayName("注册失败 - 验证码已过期")
        void register_VerifyCodeExpired() {
            // Arrange
            RegisterRequest request = new RegisterRequest();
            request.setEmail(TEST_EMAIL);
            request.setUsername(TEST_USERNAME);
            request.setPassword(TEST_PASSWORD);
            request.setCode(TEST_CODE);

            when(redisUtil.get(anyString())).thenReturn(null);

            // Act & Assert
            BusinessException exception = assertThrows(BusinessException.class,
                    () -> authService.register(request));

            assertEquals(ErrorCode.VERIFY_CODE_ERROR.getCode(), exception.getCode());
        }

        @Test
        @DisplayName("注册失败 - 邮箱已被注册")
        void register_EmailExists() {
            // Arrange
            RegisterRequest request = new RegisterRequest();
            request.setEmail(TEST_EMAIL);
            request.setUsername(TEST_USERNAME);
            request.setPassword(TEST_PASSWORD);
            request.setCode(TEST_CODE);

            when(redisUtil.get(anyString())).thenReturn(TEST_CODE);
            when(userMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

            // Act & Assert
            BusinessException exception = assertThrows(BusinessException.class,
                    () -> authService.register(request));

            assertEquals(ErrorCode.EMAIL_EXISTS.getCode(), exception.getCode());
        }

        @Test
        @DisplayName("注册失败 - 用户名已被使用")
        void register_UsernameExists() {
            // Arrange
            RegisterRequest request = new RegisterRequest();
            request.setEmail(TEST_EMAIL);
            request.setUsername(TEST_USERNAME);
            request.setPassword(TEST_PASSWORD);
            request.setCode(TEST_CODE);

            when(redisUtil.get(anyString())).thenReturn(TEST_CODE);
            when(userMapper.selectCount(any(LambdaQueryWrapper.class)))
                    .thenReturn(0L)  // 邮箱检查
                    .thenReturn(1L); // 用户名检查

            // Act & Assert
            BusinessException exception = assertThrows(BusinessException.class,
                    () -> authService.register(request));

            assertEquals(ErrorCode.USERNAME_EXISTS.getCode(), exception.getCode());
        }
    }

    // ==================== login 测试 ====================

    @Nested
    @DisplayName("login 方法测试")
    class LoginTest {

        @Test
        @DisplayName("登录成功 - 使用邮箱")
        void login_SuccessWithEmail() {
            // Arrange
            LoginRequest request = new LoginRequest();
            request.setAccount(TEST_EMAIL);
            request.setPassword(TEST_PASSWORD);

            when(redisUtil.get(anyString())).thenReturn(null);
            when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(testUser);
            when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);
            when(userMapper.updateById(any(User.class))).thenReturn(1);
            when(jwtUtil.generateToken(anyLong(), anyString())).thenReturn("test_token");

            // Act
            LoginResponse response = authService.login(request);

            // Assert
            assertNotNull(response);
            assertEquals("test_token", response.getToken());
            assertEquals("Bearer", response.getTokenType());
            verify(userMapper).updateById(any(User.class));
        }

        @Test
        @DisplayName("登录成功 - 使用用户名")
        void login_SuccessWithUsername() {
            // Arrange
            LoginRequest request = new LoginRequest();
            request.setAccount(TEST_USERNAME);
            request.setPassword(TEST_PASSWORD);

            when(redisUtil.get(anyString())).thenReturn(null);
            when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(testUser);
            when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);
            when(userMapper.updateById(any(User.class))).thenReturn(1);
            when(jwtUtil.generateToken(anyLong(), anyString())).thenReturn("test_token");

            // Act
            LoginResponse response = authService.login(request);

            // Assert
            assertNotNull(response);
            assertEquals("test_token", response.getToken());
        }

        @Test
        @DisplayName("登录失败 - 用户不存在")
        void login_UserNotFound() {
            // Arrange
            LoginRequest request = new LoginRequest();
            request.setAccount(TEST_EMAIL);
            request.setPassword(TEST_PASSWORD);

            when(redisUtil.get(anyString())).thenReturn(null);
            when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

            // Act & Assert
            BusinessException exception = assertThrows(BusinessException.class,
                    () -> authService.login(request));

            assertEquals(ErrorCode.PASSWORD_ERROR.getCode(), exception.getCode());
            verify(redisUtil).increment(anyString());
        }

        @Test
        @DisplayName("登录失败 - 密码错误")
        void login_PasswordError() {
            // Arrange
            LoginRequest request = new LoginRequest();
            request.setAccount(TEST_EMAIL);
            request.setPassword("wrong_password");

            when(redisUtil.get(anyString())).thenReturn(null);
            when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(testUser);
            when(passwordEncoder.matches(anyString(), anyString())).thenReturn(false);

            // Act & Assert
            BusinessException exception = assertThrows(BusinessException.class,
                    () -> authService.login(request));

            assertEquals(ErrorCode.PASSWORD_ERROR.getCode(), exception.getCode());
            verify(redisUtil).increment(anyString());
        }

        @Test
        @DisplayName("登录失败 - 账户已被禁用")
        void login_AccountDisabled() {
            // Arrange
            testUser.setStatus(UserStatus.DISABLED.getCode());
            LoginRequest request = new LoginRequest();
            request.setAccount(TEST_EMAIL);
            request.setPassword(TEST_PASSWORD);

            when(redisUtil.get(anyString())).thenReturn(null);
            when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(testUser);
            when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);

            // Act & Assert
            BusinessException exception = assertThrows(BusinessException.class,
                    () -> authService.login(request));

            assertEquals(ErrorCode.USER_DISABLED.getCode(), exception.getCode());
        }

        @Test
        @DisplayName("登录失败 - 登录次数过多")
        void login_TooManyAttempts() {
            // Arrange
            LoginRequest request = new LoginRequest();
            request.setAccount(TEST_EMAIL);
            request.setPassword(TEST_PASSWORD);

            when(redisUtil.get(anyString())).thenReturn("5");

            // Act & Assert
            BusinessException exception = assertThrows(BusinessException.class,
                    () -> authService.login(request));

            assertEquals(ErrorCode.LOGIN_FAILED.getCode(), exception.getCode());
        }
    }

    // ==================== logout 测试 ====================

    @Nested
    @DisplayName("logout 方法测试")
    class LogoutTest {

        @Test
        @DisplayName("退出登录成功")
        void logout_Success() {
            // Arrange
            String token = "valid_token";
            when(jwtUtil.extractJti(token)).thenReturn("jti_123");
            when(jwtUtil.getExpiration(token)).thenReturn(System.currentTimeMillis() + 3600000);

            // Act
            authService.logout(token);

            // Assert
            verify(redisUtil).set(anyString(), eq("1"), anyLong(), any());
        }

        @Test
        @DisplayName("退出登录 - Token 已过期")
        void logout_TokenExpired() {
            // Arrange
            String token = "expired_token";
            when(jwtUtil.extractJti(token)).thenReturn("jti_456");
            when(jwtUtil.getExpiration(token)).thenReturn(System.currentTimeMillis() - 1000);

            // Act
            authService.logout(token);

            // Assert
            verify(redisUtil, never()).set(anyString(), anyString(), anyLong(), any());
        }

        @Test
        @DisplayName("退出登录 - Token 为空")
        void logout_NullToken() {
            // Act
            authService.logout(null);

            // Assert
            verify(redisUtil, never()).set(anyString(), anyString(), anyLong(), any());
        }

        @Test
        @DisplayName("退出登录 - Token 解析异常")
        void logout_TokenParseError() {
            // Arrange
            String token = "invalid_token";
            when(jwtUtil.extractJti(token)).thenThrow(new RuntimeException("Invalid token"));

            // Act & Assert (should not throw)
            assertDoesNotThrow(() -> authService.logout(token));
        }
    }

    // ==================== getProfile 测试 ====================

    @Nested
    @DisplayName("getProfile 方法测试")
    class GetProfileTest {

        @Test
        @DisplayName("获取用户信息成功")
        void getProfile_Success() {
            // Arrange
            when(userMapper.selectById(TEST_USER_ID)).thenReturn(testUser);

            // Act
            UserProfileResponse response = authService.getProfile(TEST_USER_ID);

            // Assert
            assertNotNull(response);
            assertEquals(TEST_USER_ID, response.getId());
            assertEquals(TEST_USERNAME, response.getUsername());
            assertEquals(TEST_EMAIL, response.getEmail());
        }

        @Test
        @DisplayName("获取用户信息 - 用户不存在")
        void getProfile_UserNotFound() {
            // Arrange
            when(userMapper.selectById(TEST_USER_ID)).thenReturn(null);

            // Act & Assert
            BusinessException exception = assertThrows(BusinessException.class,
                    () -> authService.getProfile(TEST_USER_ID));

            assertEquals(ErrorCode.NOT_FOUND.getCode(), exception.getCode());
        }
    }

    // ==================== changePassword 测试 ====================

    @Nested
    @DisplayName("changePassword 方法测试")
    class ChangePasswordTest {

        @Test
        @DisplayName("修改密码成功")
        void changePassword_Success() {
            // Arrange
            ChangePasswordRequest request = new ChangePasswordRequest();
            request.setOldPassword(TEST_PASSWORD);
            request.setNewPassword("new_password");

            when(userMapper.selectById(TEST_USER_ID)).thenReturn(testUser);
            // 第一次 matches: 旧密码校验 → true; 第二次 matches: 新旧是否相同 → false
            when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true, false);
            when(passwordEncoder.encode(anyString())).thenReturn("new_encoded_password");
            when(userMapper.updateById(any(User.class))).thenReturn(1);
            when(jwtUtil.extractJti("test_token")).thenReturn("jti_123");
            when(jwtUtil.getExpiration("test_token")).thenReturn(System.currentTimeMillis() + 3600000);

            // Act
            authService.changePassword(TEST_USER_ID, request, "test_token");

            // Assert
            verify(userMapper).updateById(any(User.class));
            verify(redisUtil).set(eq("token:blacklist:jti_123"), eq("1"), anyLong(), any(TimeUnit.class));
        }

        @Test
        @DisplayName("修改密码失败 - 用户不存在")
        void changePassword_UserNotFound() {
            // Arrange
            ChangePasswordRequest request = new ChangePasswordRequest();
            request.setOldPassword(TEST_PASSWORD);
            request.setNewPassword("new_password");

            when(userMapper.selectById(TEST_USER_ID)).thenReturn(null);

            // Act & Assert
            BusinessException exception = assertThrows(BusinessException.class,
                    () -> authService.changePassword(TEST_USER_ID, request, "test_token"));

            assertEquals(ErrorCode.NOT_FOUND.getCode(), exception.getCode());
        }

        @Test
        @DisplayName("修改密码失败 - 旧密码错误")
        void changePassword_OldPasswordError() {
            // Arrange
            ChangePasswordRequest request = new ChangePasswordRequest();
            request.setOldPassword("wrong_password");
            request.setNewPassword("new_password");

            when(userMapper.selectById(TEST_USER_ID)).thenReturn(testUser);
            when(passwordEncoder.matches(anyString(), anyString())).thenReturn(false);

            // Act & Assert
            BusinessException exception = assertThrows(BusinessException.class,
                    () -> authService.changePassword(TEST_USER_ID, request, "test_token"));

            assertEquals(ErrorCode.PASSWORD_ERROR.getCode(), exception.getCode());
            verify(userMapper, never()).updateById(any(User.class));
        }
    }

    // ==================== updateProfile 测试 ====================

    @Nested
    @DisplayName("updateProfile 方法测试")
    class UpdateProfileTest {

        @Test
        @DisplayName("更新用户信息成功")
        void updateProfile_Success() {
            UpdateProfileRequest request = new UpdateProfileRequest();
            request.setNickname("新昵称");
            request.setAvatar("https://example.com/avatar.jpg");

            when(userMapper.selectById(TEST_USER_ID)).thenReturn(testUser);
            when(userMapper.updateById(any(User.class))).thenReturn(1);

            authService.updateProfile(TEST_USER_ID, request);

            verify(userMapper).updateById(any(User.class));
            assertEquals("新昵称", testUser.getNickname());
            assertEquals("https://example.com/avatar.jpg", testUser.getAvatar());
        }

        @Test
        @DisplayName("更新用户信息 - 仅更新昵称")
        void updateProfile_NicknameOnly() {
            UpdateProfileRequest request = new UpdateProfileRequest();
            request.setNickname("新昵称");

            when(userMapper.selectById(TEST_USER_ID)).thenReturn(testUser);
            when(userMapper.updateById(any(User.class))).thenReturn(1);

            authService.updateProfile(TEST_USER_ID, request);

            assertEquals("新昵称", testUser.getNickname());
            assertNull(testUser.getAvatar());
        }

        @Test
        @DisplayName("更新用户信息 - 用户不存在")
        void updateProfile_UserNotFound() {
            UpdateProfileRequest request = new UpdateProfileRequest();
            request.setNickname("新昵称");

            when(userMapper.selectById(TEST_USER_ID)).thenReturn(null);

            BusinessException exception = assertThrows(BusinessException.class,
                    () -> authService.updateProfile(TEST_USER_ID, request));

            assertEquals(ErrorCode.NOT_FOUND.getCode(), exception.getCode());
            verify(userMapper, never()).updateById(any(User.class));
        }
    }
}
