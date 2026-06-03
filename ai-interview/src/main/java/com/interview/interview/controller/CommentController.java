package com.interview.interview.controller;

import com.interview.common.result.PageResult;
import com.interview.common.result.Result;
import com.interview.common.utils.SecurityUtils;
import com.interview.interview.dto.CommentRequest;
import com.interview.interview.dto.CommentResponse;
import com.interview.interview.service.CommentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 评论控制器
 * <p>
 * 提供面经评论的查看、发表、删除接口。
 * </p>
 */
@Tag(name = "面经评论模块")
@RestController
@RequiredArgsConstructor
public class CommentController {

    private final CommentService commentService;

    /**
     * 获取面经评论列表（无需认证）
     *
     * @param token 分享 Token
     * @param page  页码
     * @param size  每页大小
     * @return 评论列表（分页）
     */
    @Operation(summary = "获取面经评论列表（无需认证）")
    @GetMapping("/experiences/{token}/comments")
    public Result<PageResult<CommentResponse>> listComments(
            @PathVariable String token,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.success(commentService.listByShareToken(token, page, size));
    }

    /**
     * 发表评论（需要认证）
     *
     * @param token   分享 Token
     * @param request 评论内容
     * @return 操作结果
     */
    @Operation(summary = "发表评论")
    @PostMapping("/experiences/{token}/comments")
    public Result<Void> addComment(@PathVariable String token,
                                   @Valid @RequestBody CommentRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        commentService.addComment(token, userId, request.getContent());
        return Result.success();
    }

    /**
     * 删除评论（仅评论作者可删除）
     *
     * @param id 评论 ID
     * @return 操作结果
     */
    @Operation(summary = "删除评论")
    @DeleteMapping("/comments/{id}")
    public Result<Void> deleteComment(@PathVariable Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        commentService.deleteComment(id, userId);
        return Result.success();
    }
}
