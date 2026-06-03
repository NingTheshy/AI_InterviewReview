package com.interview.interview.controller;

import com.interview.common.result.PageResult;
import com.interview.common.result.Result;
import com.interview.common.utils.SecurityUtils;
import com.interview.interview.dto.FavoriteRequest;
import com.interview.interview.dto.FavoriteResponse;
import com.interview.interview.service.FavoriteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 收藏控制器
 * <p>
 * 提供面试问题的收藏、取消收藏和列表查询接口。
 * </p>
 */
@Tag(name = "问题收藏模块")
@RestController
@RequestMapping("/favorites")
@RequiredArgsConstructor
public class FavoriteController {

    private final FavoriteService favoriteService;

    /**
     * 收藏问题
     *
     * @param request 收藏请求（面试 ID、问题 ID、备注）
     * @return 操作结果
     */
    @Operation(summary = "收藏问题")
    @PostMapping
    public Result<Void> addFavorite(@Valid @RequestBody FavoriteRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        favoriteService.addFavorite(userId, request.getInterviewId(),
                request.getQuestionId(), request.getRemark());
        return Result.success();
    }

    /**
     * 取消收藏
     *
     * @param id 收藏记录 ID
     * @return 操作结果
     */
    @Operation(summary = "取消收藏")
    @DeleteMapping("/{id}")
    public Result<Void> removeFavorite(@PathVariable Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        favoriteService.removeFavorite(id, userId);
        return Result.success();
    }

    /**
     * 获取当前用户的收藏列表
     *
     * @param page 页码
     * @param size 每页大小
     * @return 收藏列表（分页）
     */
    @Operation(summary = "获取收藏列表")
    @GetMapping
    public Result<PageResult<FavoriteResponse>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        Long userId = SecurityUtils.getCurrentUserId();
        return Result.success(favoriteService.list(userId, page, size));
    }
}
