package com.interview.admin.controller;

import com.interview.admin.dto.ExperienceManagementResponse;
import com.interview.admin.dto.AdminExperienceQuery;
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
 * 只管理已发布面经的查看、下架与重新上架，按权限码分别授权。
 * </p>
 */
@Tag(name = "管理员 - 面经管理")
@RestController
@RequestMapping("/admin/experiences")
@RequiredArgsConstructor
public class AdminExperienceController {

    private final AdminService adminService;

    /**
     * 获取面经列表（分页）
     *
     * @param query 分页和组合筛选条件
     * @return 面经管理列表
     */
    @Operation(summary = "获取面经列表")
    @GetMapping
    @PreAuthorize("hasAuthority('experience:read_all')")
    public Result<PageResult<ExperienceManagementResponse>> listExperiences(
            @ModelAttribute AdminExperienceQuery query) {
        return Result.success(adminService.searchExperiences(query));
    }

    @Operation(summary = "查看面经公开快照")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('experience:read_all')")
    public Result<com.interview.admin.dto.ExperienceManagementDetailResponse> getDetail(@PathVariable Long id) {
        return Result.success(adminService.getExperienceDetail(id));
    }

    /**
     * 设置面经公开状态（下架/恢复）
     *
     * @param id      面经 ID
     * @param request 状态更新请求（isPublic: 0=下架，1=上架）
     * @return 操作结果
     */
    @Operation(summary = "设置面经状态")
    @PutMapping("/{id}/status")
    @PreAuthorize("hasAuthority('experience:manage')")
    public Result<Void> setExperienceStatus(
            @PathVariable Long id,
            @Valid @RequestBody ExperienceStatusRequest request) {
        adminService.setExperienceStatus(id, request.getIsPublic());
        return Result.success();
    }
}
