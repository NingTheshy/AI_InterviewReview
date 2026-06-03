package com.interview.auth.controller;

import com.interview.auth.dto.*;
import com.interview.auth.service.AuthService;
import com.interview.common.result.Result;
import com.interview.common.utils.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 认证控制器
 * <p>
 * 提供用户注册、登录、登出、密码修改等接口。
 * </p>
 */
@Tag(name = "认证模块")
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "发送邮箱验证码")
    @PostMapping("/send-code")
    public Result<Void> sendCode(@Valid @RequestBody SendCodeRequest request) {
        authService.sendVerifyCode(request.getEmail());
        return Result.success();
    }

    @Operation(summary = "用户注册")
    @PostMapping("/register")
    public Result<Void> register(@Valid @RequestBody RegisterRequest request) {
        authService.register(request);
        return Result.success();
    }

    @Operation(summary = "用户登录")
    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return Result.success(authService.login(request));
    }

    @Operation(summary = "退出登录")
    @PostMapping("/logout")
    public Result<Void> logout(@RequestHeader(value = "Authorization", required = false) String authorization) {
        String token = (authorization != null) ? authorization.replace("Bearer ", "") : null;
        authService.logout(token);
        return Result.success();
    }

    @Operation(summary = "获取当前用户信息")
    @GetMapping("/profile")
    public Result<UserProfileResponse> getProfile() {
        Long userId = SecurityUtils.getCurrentUserId();
        return Result.success(authService.getProfile(userId));
    }

    @Operation(summary = "修改密码")
    @PutMapping("/password")
    public Result<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request,
                                       @RequestHeader(value = "Authorization", required = false) String authorization) {
        Long userId = SecurityUtils.getCurrentUserId();
        String token = (authorization != null) ? authorization.replace("Bearer ", "") : null;
        authService.changePassword(userId, request, token);
        return Result.success();
    }

    @Operation(summary = "更新用户信息")
    @PutMapping("/profile")
    public Result<Void> updateProfile(@Valid @RequestBody UpdateProfileRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        authService.updateProfile(userId, request);
        return Result.success();
    }
}
