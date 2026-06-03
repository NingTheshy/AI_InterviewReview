package com.interview.auth.service;

import com.interview.auth.dto.*;

/**
 * 认证服务接口
 * <p>
 * 提供用户注册、登录、登出、密码修改等认证功能。
 * </p>
 */
public interface AuthService {

    /**
     * 发送邮箱验证码
     *
     * @param email 邮箱地址
     */
    void sendVerifyCode(String email);

    /**
     * 用户注册
     *
     * @param request 注册请求
     */
    void register(RegisterRequest request);

    /**
     * 用户登录
     *
     * @param request 登录请求
     * @return 登录响应（含 Token）
     */
    LoginResponse login(LoginRequest request);

    /**
     * 用户登出
     *
     * @param token JWT Token
     */
    void logout(String token);

    /**
     * 获取用户资料
     *
     * @param userId 用户 ID
     * @return 用户资料
     */
    UserProfileResponse getProfile(Long userId);

    /**
     * 修改密码
     *
     * @param userId  用户 ID
     * @param request 修改密码请求
     * @param token   当前有效的 JWT Token（用于失效处理）
     */
    void changePassword(Long userId, ChangePasswordRequest request, String token);

    /**
     * 更新用户资料
     *
     * @param userId  用户 ID
     * @param request 更新请求
     */
    void updateProfile(Long userId, UpdateProfileRequest request);
}
