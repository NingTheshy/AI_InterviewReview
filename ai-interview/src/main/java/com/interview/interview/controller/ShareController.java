package com.interview.interview.controller;

import com.interview.common.result.Result;
import com.interview.common.utils.SecurityUtils;
import com.interview.interview.dto.ExperienceDetailResponse;
import com.interview.interview.dto.ShareRequest;
import com.interview.interview.dto.ShareResponse;
import com.interview.interview.service.ShareService;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 分享控制器
 * <p>
 * 提供面试记录的分享链接生成、公开状态切换、分享详情查看等接口。
 * </p>
 */
@Tag(name = "面试分享模块")
@RestController
@RequiredArgsConstructor
public class ShareController {

    private final ShareService shareService;

    /**
     * 生成分享链接 / 发布面经
     *
     * @param id      面试 ID
     * @param request 分享请求（过期类型、是否公开）
     * @return 分享记录
     */
    @Operation(summary = "生成分享链接/发布面经")
    @PostMapping("/interviews/{id}/share")
    public Result<ShareResponse> createShare(@PathVariable Long id,
                                              @Valid @RequestBody ShareRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        return Result.success(shareService.createShare(id, userId,
                request.getExpireType(), request.getIsPublic()));
    }

    /**
     * 获取面试记录的分享列表
     *
     * @param id 面试 ID
     * @return 分享记录列表
     */
    @Operation(summary = "获取分享记录列表")
    @GetMapping("/interviews/{id}/shares")
    public Result<List<ShareResponse>> listShares(@PathVariable Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        return Result.success(shareService.listShares(id, userId));
    }

    /**
     * 撤销分享链接 / 下架面经
     *
     * @param token 分享 Token
     * @return 操作结果
     */
    @Operation(summary = "撤销分享链接/下架面经")
    @DeleteMapping("/shares/{token}")
    public Result<Void> deleteShare(@PathVariable String token) {
        Long userId = SecurityUtils.getCurrentUserId();
        shareService.deleteShare(token, userId);
        return Result.success();
    }

    /**
     * 切换面经公开状态
     *
     * @param token 分享 Token
     * @return 操作结果
     */
    @Operation(summary = "切换面经公开状态")
    @PutMapping("/shares/{token}/public")
    public Result<Void> togglePublic(@PathVariable String token) {
        Long userId = SecurityUtils.getCurrentUserId();
        shareService.togglePublic(token, userId);
        return Result.success();
    }

    /**
     * 访问分享页面（无需认证）
     *
     * @param token 分享 Token
     * @return 分享详情
     */
    @Operation(summary = "访问分享页面（无需认证）")
    @GetMapping("/share/{token}")
    public Result<ExperienceDetailResponse> getShareDetail(@PathVariable String token) {
        return Result.success(shareService.getShareDetail(token));
    }
}
