package com.interview.admin.controller;

import com.interview.admin.dto.CommentManagementResponse;
import com.interview.admin.service.AdminService;
import com.interview.common.result.PageResult;
import com.interview.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * 管理员 - 评论管理控制器
 * <p>
 * 提供查看所有评论、删除任意评论的接口。
 * 所有接口需要 admin 角色权限。
 * </p>
 */
@Tag(name = "管理员 - 评论管理")
@RestController
@RequestMapping("/admin/comments")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminCommentController {

    private final AdminService adminService;

    /**
     * 获取评论列表（分页，支持按用户筛选）
     *
     * @param page   页码，默认 1
     * @param size   每页数量，默认 10
     * @param userId 用户 ID 筛选，可选
     * @return 评论管理列表
     */
    @Operation(summary = "获取评论列表")
    @GetMapping
    public Result<PageResult<CommentManagementResponse>> listComments(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Long userId) {
        return Result.success(adminService.listComments(page, size, userId));
    }

    /**
     * 删除评论（逻辑删除）
     *
     * @param id 评论 ID
     * @return 操作结果
     */
    @Operation(summary = "删除评论")
    @DeleteMapping("/{id}")
    public Result<Void> deleteComment(@PathVariable Long id) {
        adminService.deleteComment(id);
        return Result.success();
    }
}
