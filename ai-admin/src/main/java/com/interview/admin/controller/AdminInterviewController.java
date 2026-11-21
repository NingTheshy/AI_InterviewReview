package com.interview.admin.controller;

import com.interview.admin.dto.InterviewManagementResponse;
import com.interview.admin.dto.AdminInterviewQuery;
import com.interview.admin.dto.AdminInterviewEditRequest;
import com.interview.admin.service.AdminService;
import com.interview.common.result.PageResult;
import com.interview.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * 管理员 - 面试管理控制器
 * <p>
 * 管理原始面试记录与七天回收站；读取和修改分别由权限码控制。
 * </p>
 */
@Tag(name = "管理员 - 面试管理")
@RestController
@RequestMapping("/admin/interviews")
@RequiredArgsConstructor
public class AdminInterviewController {

    private final AdminService adminService;

    /**
     * 获取面试列表（分页，支持筛选）
     *
     * @param query 分页和组合筛选条件
     * @return 面试管理列表
     */
    @Operation(summary = "获取面试列表")
    @GetMapping
    @PreAuthorize("hasAuthority('interview:read_all')")
    public Result<PageResult<InterviewManagementResponse>> listInterviews(
            @ModelAttribute AdminInterviewQuery query) {
        return Result.success(adminService.searchInterviews(query));
    }

    @Operation(summary = "查看完整面试复盘")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('interview:read_all')")
    public Result<com.interview.interview.dto.InterviewDetailResponse> detail(@PathVariable Long id) {
        return Result.success(adminService.getInterviewDetail(id));
    }

    @Operation(summary = "取消处理中的面试")
    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('interview:manage_all')")
    public Result<Void> cancel(@PathVariable Long id) {
        adminService.cancelInterview(id);
        return Result.success();
    }

    @Operation(summary = "重试失败或已取消的面试")
    @PostMapping("/{id}/retry")
    @PreAuthorize("hasAuthority('interview:manage_all')")
    public Result<Void> retry(@PathVariable Long id) {
        adminService.retryInterview(id);
        return Result.success();
    }

    @Operation(summary = "修改面试基础信息")
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('interview:manage_all')")
    public Result<Void> edit(@PathVariable Long id, @Valid @RequestBody AdminInterviewEditRequest request) {
        adminService.editInterview(id, request);
        return Result.success();
    }

    @Operation(summary = "将面试移入七天回收站")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('interview:manage_all')")
    public Result<Void> delete(@PathVariable Long id) {
        adminService.deleteInterview(id);
        return Result.success();
    }

    @Operation(summary = "恢复回收站内面试")
    @PostMapping("/{id}/restore")
    @PreAuthorize("hasAuthority('interview:manage_all')")
    public Result<Void> restore(@PathVariable Long id) {
        adminService.restoreInterview(id);
        return Result.success();
    }
}
