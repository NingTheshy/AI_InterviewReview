package com.interview.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.interview.auth.dto.*;
import com.interview.auth.service.AuthService;
import com.interview.common.exception.BusinessException;
import com.interview.common.exception.GlobalExceptionHandler;
import com.interview.common.result.Result;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * AuthController 功能测试
 * <p>
 * 使用 MockMvc 测试 REST API 端点。
 * 注意：此测试使用 standalone 模式，不包含 Spring Security 过滤器链。
 * </p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AuthController 功能测试")
class AuthControllerTest {

    private MockMvc mockMvc;

    private ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private AuthService authService;

    @InjectMocks
    private AuthController authController;

    private static final String BASE_URL = "/auth";

    private static final Long TEST_USER_ID = 1L;

    @BeforeEach
    void setUp() {
        // 使用 standalone 模式，添加 GlobalExceptionHandler 处理业务异常
        mockMvc = MockMvcBuilders.standaloneSetup(authController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        // 设置安全上下文，模拟已认证用户
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(TEST_USER_ID, "testuser",
                        List.of(new SimpleGrantedAuthority("ROLE_USER"))));
    }

    // ==================== 发送验证码测试 ====================

    @Nested
    @DisplayName("POST /auth/send-code 测试")
    class SendCodeTest {

        @Test
        @DisplayName("发送验证码成功")
        void sendCode_Success() throws Exception {
            // Arrange
            SendCodeRequest request = new SendCodeRequest();
            request.setEmail("test@example.com");
            doNothing().when(authService).sendVerifyCode(anyString());

            // Act & Assert
            mockMvc.perform(post(BASE_URL + "/send-code")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));

            verify(authService).sendVerifyCode("test@example.com");
        }

        @Test
        @DisplayName("发送验证码 - 邮箱格式错误")
        void sendCode_InvalidEmail() throws Exception {
            // Arrange
            SendCodeRequest request = new SendCodeRequest();
            request.setEmail("invalid-email");

            // Act & Assert
            mockMvc.perform(post(BASE_URL + "/send-code")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("发送验证码 - 邮箱为空")
        void sendCode_EmptyEmail() throws Exception {
            // Arrange
            SendCodeRequest request = new SendCodeRequest();
            request.setEmail("");

            // Act & Assert
            mockMvc.perform(post(BASE_URL + "/send-code")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("发送验证码 - 频率限制")
        void sendCode_RateLimit() throws Exception {
            // Arrange
            SendCodeRequest request = new SendCodeRequest();
            request.setEmail("test@example.com");
            doThrow(new BusinessException(1007, "验证码发送过于频繁，请稍后再试"))
                    .when(authService).sendVerifyCode(anyString());

            // Act & Assert
            mockMvc.perform(post(BASE_URL + "/send-code")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(1007));
        }
    }

    // ==================== 注册测试 ====================

    @Nested
    @DisplayName("POST /auth/register 测试")
    class RegisterTest {

        @Test
        @DisplayName("注册成功")
        void register_Success() throws Exception {
            // Arrange
            RegisterRequest request = new RegisterRequest();
            request.setEmail("test@example.com");
            request.setUsername("testuser");
            request.setPassword("password123");
            request.setCode("123456");
            doNothing().when(authService).register(any(RegisterRequest.class));

            // Act & Assert
            mockMvc.perform(post(BASE_URL + "/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));

            verify(authService).register(any(RegisterRequest.class));
        }

        @Test
        @DisplayName("注册失败 - 验证码错误")
        void register_VerifyCodeError() throws Exception {
            // Arrange
            RegisterRequest request = new RegisterRequest();
            request.setEmail("test@example.com");
            request.setUsername("testuser");
            request.setPassword("password123");
            request.setCode("000000");
            doThrow(new BusinessException(1006, "验证码错误或已过期"))
                    .when(authService).register(any(RegisterRequest.class));

            // Act & Assert
            mockMvc.perform(post(BASE_URL + "/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(1006));
        }

        @Test
        @DisplayName("注册失败 - 邮箱已存在")
        void register_EmailExists() throws Exception {
            // Arrange
            RegisterRequest request = new RegisterRequest();
            request.setEmail("existing@example.com");
            request.setUsername("testuser");
            request.setPassword("password123");
            request.setCode("123456");
            doThrow(new BusinessException(1005, "邮箱已被注册"))
                    .when(authService).register(any(RegisterRequest.class));

            // Act & Assert
            mockMvc.perform(post(BASE_URL + "/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(1005));
        }

        @Test
        @DisplayName("注册失败 - 缺少必填字段")
        void register_MissingFields() throws Exception {
            // Arrange
            RegisterRequest request = new RegisterRequest();
            // 缺少 email, username, password, code

            // Act & Assert
            mockMvc.perform(post(BASE_URL + "/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("注册失败 - 密码长度不足")
        void register_PasswordTooShort() throws Exception {
            // Arrange
            RegisterRequest request = new RegisterRequest();
            request.setEmail("test@example.com");
            request.setUsername("testuser");
            request.setPassword("123"); // 太短
            request.setCode("123456");

            // Act & Assert
            mockMvc.perform(post(BASE_URL + "/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }
    }

    // ==================== 登录测试 ====================

    @Nested
    @DisplayName("POST /auth/login 测试")
    class LoginTest {

        @Test
        @DisplayName("登录成功")
        void login_Success() throws Exception {
            // Arrange
            LoginRequest request = new LoginRequest();
            request.setAccount("test@example.com");
            request.setPassword("password123");

            LoginResponse.UserInfo userInfo = LoginResponse.UserInfo.builder()
                    .id(1L)
                    .username("testuser")
                    .email("test@example.com")
                    .nickname("Test User")
                    .role(0)
                    .build();

            LoginResponse response = LoginResponse.builder()
                    .token("test_token")
                    .tokenType("Bearer")
                    .expiresIn(604800L)
                    .user(userInfo)
                    .build();

            when(authService.login(any(LoginRequest.class))).thenReturn(response);

            // Act & Assert
            mockMvc.perform(post(BASE_URL + "/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.token").value("test_token"))
                    .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                    .andExpect(jsonPath("$.data.user.username").value("testuser"));
        }

        @Test
        @DisplayName("登录失败 - 密码错误")
        void login_PasswordError() throws Exception {
            // Arrange
            LoginRequest request = new LoginRequest();
            request.setAccount("test@example.com");
            request.setPassword("wrong_password");

            when(authService.login(any(LoginRequest.class)))
                    .thenThrow(new BusinessException(1002, "密码错误"));

            // Act & Assert
            mockMvc.perform(post(BASE_URL + "/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value(1002))
                    .andExpect(jsonPath("$.message").value("密码错误"));
        }

        @Test
        @DisplayName("登录失败 - 账户被禁用")
        void login_AccountDisabled() throws Exception {
            // Arrange
            LoginRequest request = new LoginRequest();
            request.setAccount("test@example.com");
            request.setPassword("password123");

            when(authService.login(any(LoginRequest.class)))
                    .thenThrow(new BusinessException(1003, "账户已被禁用"));

            // Act & Assert
            mockMvc.perform(post(BASE_URL + "/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value(1003));
        }

        @Test
        @DisplayName("登录失败 - 缺少参数")
        void login_MissingParams() throws Exception {
            // Arrange
            LoginRequest request = new LoginRequest();
            // 缺少 account 和 password

            // Act & Assert
            mockMvc.perform(post(BASE_URL + "/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }
    }

    // ==================== 退出登录测试 ====================

    @Nested
    @DisplayName("POST /auth/logout 测试")
    class LogoutTest {

        @Test
        @DisplayName("退出登录成功")
        void logout_Success() throws Exception {
            // Arrange
            doNothing().when(authService).logout(anyString());

            // Act & Assert
            mockMvc.perform(post(BASE_URL + "/logout")
                            .header("Authorization", "Bearer test_token"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));

            verify(authService).logout("test_token");
        }

        @Test
        @DisplayName("退出登录 - 缺少 Token")
        void logout_MissingToken() throws Exception {
            // Act & Assert
            mockMvc.perform(post(BASE_URL + "/logout"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));
        }
    }

    // ==================== 获取用户信息测试 ====================

    @Nested
    @DisplayName("GET /auth/profile 测试")
    class GetProfileTest {

        @Test
        @DisplayName("获取用户信息成功")
        void getProfile_Success() throws Exception {
            // Arrange
            UserProfileResponse response = UserProfileResponse.builder()
                    .id(1L)
                    .username("testuser")
                    .email("test@example.com")
                    .nickname("Test User")
                    .avatar("avatar.jpg")
                    .role(0)
                    .build();

            when(authService.getProfile(anyLong())).thenReturn(response);

            // Act & Assert
            mockMvc.perform(get(BASE_URL + "/profile"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.username").value("testuser"))
                    .andExpect(jsonPath("$.data.email").value("test@example.com"));
        }

        @Test
        @DisplayName("获取用户信息 - 用户不存在")
        void getProfile_UserNotFound() throws Exception {
            // Arrange
            when(authService.getProfile(anyLong()))
                    .thenThrow(new BusinessException(404, "资源不存在"));

            // Act & Assert
            mockMvc.perform(get(BASE_URL + "/profile"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value(404));
        }
    }

    // ==================== 修改密码测试 ====================

    @Nested
    @DisplayName("PUT /auth/password 测试")
    class ChangePasswordTest {

        @Test
        @DisplayName("修改密码成功")
        void changePassword_Success() throws Exception {
            // Arrange
            ChangePasswordRequest request = new ChangePasswordRequest();
            request.setOldPassword("old_password");
            request.setNewPassword("new_password");

            doNothing().when(authService).changePassword(anyLong(), any(ChangePasswordRequest.class), any());

            // Act & Assert
            mockMvc.perform(put(BASE_URL + "/password")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));

            verify(authService).changePassword(anyLong(), any(ChangePasswordRequest.class), any());
        }

        @Test
        @DisplayName("修改密码失败 - 旧密码错误")
        void changePassword_OldPasswordError() throws Exception {
            // Arrange
            ChangePasswordRequest request = new ChangePasswordRequest();
            request.setOldPassword("wrong_password");
            request.setNewPassword("new_password");

            doThrow(new BusinessException(1002, "密码错误"))
                    .when(authService).changePassword(anyLong(), any(ChangePasswordRequest.class), any());

            // Act & Assert
            mockMvc.perform(put(BASE_URL + "/password")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value(1002));
        }

        @Test
        @DisplayName("修改密码 - 缺少参数")
        void changePassword_MissingParams() throws Exception {
            // Arrange
            ChangePasswordRequest request = new ChangePasswordRequest();
            // 缺少 oldPassword 和 newPassword

            // Act & Assert
            mockMvc.perform(put(BASE_URL + "/password")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("修改密码 - 新密码长度不足")
        void changePassword_NewPasswordTooShort() throws Exception {
            // Arrange
            ChangePasswordRequest request = new ChangePasswordRequest();
            request.setOldPassword("old_password");
            request.setNewPassword("123"); // 太短

            // Act & Assert
            mockMvc.perform(put(BASE_URL + "/password")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }
    }

    // ==================== 更新用户信息测试 ====================

    @Nested
    @DisplayName("PUT /auth/profile 测试")
    class UpdateProfileTest {

        @Test
        @DisplayName("更新用户信息成功")
        void updateProfile_Success() throws Exception {
            UpdateProfileRequest request = new UpdateProfileRequest();
            request.setNickname("新昵称");
            request.setAvatar("https://example.com/avatar.jpg");

            doNothing().when(authService).updateProfile(anyLong(), any(UpdateProfileRequest.class));

            mockMvc.perform(put(BASE_URL + "/profile")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));

            verify(authService).updateProfile(anyLong(), any(UpdateProfileRequest.class));
        }

        @Test
        @DisplayName("更新用户信息 - 仅更新昵称")
        void updateProfile_NicknameOnly() throws Exception {
            UpdateProfileRequest request = new UpdateProfileRequest();
            request.setNickname("新昵称");

            doNothing().when(authService).updateProfile(anyLong(), any(UpdateProfileRequest.class));

            mockMvc.perform(put(BASE_URL + "/profile")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));
        }

        @Test
        @DisplayName("更新用户信息 - 用户不存在")
        void updateProfile_UserNotFound() throws Exception {
            UpdateProfileRequest request = new UpdateProfileRequest();
            request.setNickname("新昵称");

            doThrow(new BusinessException(404, "资源不存在"))
                    .when(authService).updateProfile(anyLong(), any(UpdateProfileRequest.class));

            mockMvc.perform(put(BASE_URL + "/profile")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value(404));
        }
    }
}
