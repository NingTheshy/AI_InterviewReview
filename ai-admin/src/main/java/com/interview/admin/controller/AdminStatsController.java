package com.interview.admin.controller;

import com.interview.admin.dto.StatsOverviewResponse;
import com.interview.admin.service.AdminService;
import com.interview.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理员 - 系统统计控制器
 * <p>
 * 提供系统数据统计接口。
 * 所有接口需要 admin 角色权限。
 * </p>
 */
@Tag(name = "管理员 - 系统统计")
@RestController
@RequestMapping("/admin/stats")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminStatsController {

    private final AdminService adminService;

    /**
     * 获取系统统计概览
     */
    @Operation(summary = "获取系统统计概览")
    @GetMapping("/overview")
    public Result<StatsOverviewResponse> getStatsOverview() {
        return Result.success(adminService.getStatsOverview());
    }
}
