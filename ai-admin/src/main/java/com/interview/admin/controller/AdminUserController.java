package com.interview.admin.controller;

import com.interview.admin.dto.UserManagementResponse;
import com.interview.admin.dto.UserRoleRequest;
import com.interview.admin.dto.UserStatusRequest;
import com.interview.admin.service.AdminService;
import com.interview.common.result.PageResult;
import com.interview.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * 管理员 - 用户管理控制器
 * <p>
 * 提供用户列表、详情、状态管理、角色管理等接口。
 * 每个接口均使用权限码授权，不根据角色名称直接放行。
 * </p>
 */
@Tag(name = "管理员 - 用户管理")
@RestController
@RequestMapping("/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final AdminService adminService;

    /**
     * 获取用户列表（分页，支持筛选）
     */
    @Operation(summary = "获取用户列表")
    @GetMapping
    @PreAuthorize("hasAuthority('user:read')")
    public Result<PageResult<UserManagementResponse>> listUsers(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status) {
        return Result.success(adminService.listUsers(page, size, keyword, status));
    }

    /**
     * 获取用户详情
     */
    @Operation(summary = "获取用户详情")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('user:read')")
    public Result<UserManagementResponse> getUserDetail(@PathVariable Long id) {
        return Result.success(adminService.getUserDetail(id));
    }

    /**
     * 更新用户状态（启用/禁用）
     */
    @Operation(summary = "更新用户状态")
    @PutMapping("/{id}/status")
    @PreAuthorize("hasAuthority('user:status:update')")
    public Result<Void> updateUserStatus(
            @PathVariable Long id,
            @Valid @RequestBody UserStatusRequest request) {
        adminService.updateUserStatus(com.interview.common.utils.SecurityUtils.getCurrentUserId(),
                id, request.getStatus());
        return Result.success();
    }

    /**
     * 更新用户角色
     */
    @Operation(summary = "更新用户角色")
    @PutMapping("/{id}/role")
    @PreAuthorize("hasAuthority('user:role:assign')")
    public Result<Void> updateUserRole(
            @PathVariable Long id,
            @Valid @RequestBody UserRoleRequest request) {
        adminService.updateUserRoles(com.interview.common.utils.SecurityUtils.getCurrentUserId(),
                id, request.getRoles());
        return Result.success();
    }
}
