package com.interview.admin.controller;

import com.interview.admin.dto.ExperienceManagementResponse;
import com.interview.admin.dto.ExperienceStatusRequest;
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
 * 管理员 - 面经管理控制器
 * <p>
 * 提供查看所有面经、下架/恢复面经的接口。
 * 所有接口需要 admin 角色权限。
 * </p>
 */
@Tag(name = "管理员 - 面经管理")
@RestController
@RequestMapping("/admin/experiences")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminExperienceController {

    private final AdminService adminService;

    /**
     * 获取面经列表（分页）
     *
     * @param page 页码，默认 1
     * @param size 每页数量，默认 10
     * @return 面经管理列表
     */
    @Operation(summary = "获取面经列表")
    @GetMapping
    public Result<PageResult<ExperienceManagementResponse>> listExperiences(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return Result.success(adminService.listExperiences(page, size));
    }

    /**
     * 设置面经公开状态（下架/恢复）
     *
     * @param token   分享 Token
     * @param request 状态更新请求（isPublic: 0=下架，1=上架）
     * @return 操作结果
     */
    @Operation(summary = "设置面经状态")
    @PutMapping("/{token}/status")
    public Result<Void> setExperienceStatus(
            @PathVariable String token,
            @Valid @RequestBody ExperienceStatusRequest request) {
        adminService.setExperienceStatus(token, request.getIsPublic());
        return Result.success();
    }
}
