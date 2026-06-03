package com.interview.admin.controller;

import com.interview.admin.dto.InterviewManagementResponse;
import com.interview.admin.service.AdminService;
import com.interview.common.result.PageResult;
import com.interview.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * 管理员 - 面试管理控制器
 * <p>
 * 提供查看所有用户面试记录的接口，支持按用户和状态筛选。
 * 所有接口需要 admin 角色权限。
 * </p>
 */
@Tag(name = "管理员 - 面试管理")
@RestController
@RequestMapping("/admin/interviews")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminInterviewController {

    private final AdminService adminService;

    /**
     * 获取面试列表（分页，支持筛选）
     *
     * @param page   页码，默认 1
     * @param size   每页数量，默认 10
     * @param userId 用户 ID 筛选，可选
     * @param status 面试状态筛选，可选
     * @return 面试管理列表
     */
    @Operation(summary = "获取面试列表")
    @GetMapping
    public Result<PageResult<InterviewManagementResponse>> listInterviews(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) Integer status) {
        return Result.success(adminService.listInterviews(page, size, userId, status));
    }
}
