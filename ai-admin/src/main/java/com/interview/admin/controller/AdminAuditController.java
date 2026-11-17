package com.interview.admin.controller;

import com.interview.admin.dto.AdminAuditLogResponse;
import com.interview.admin.service.impl.AdminAuditReadService;
import com.interview.common.result.PageResult;
import com.interview.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 只读审计日志入口；审计记录不提供修改和删除接口。 */
@Tag(name = "管理员 - 审计日志")
@RestController
@RequestMapping("/admin/audit-logs")
@RequiredArgsConstructor
public class AdminAuditController {

    private final AdminAuditReadService auditReadService;

    /** 分页查看角色、状态、配置及内容管理等高风险操作。 */
    @Operation(summary = "获取权限与高风险操作审计日志")
    @GetMapping
    @PreAuthorize("hasAuthority('audit:read')")
    public Result<PageResult<AdminAuditLogResponse>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.success(auditReadService.list(page, size));
    }
}
