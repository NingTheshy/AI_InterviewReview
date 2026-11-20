package com.interview.interview.controller;

import com.interview.common.result.PageResult;
import com.interview.common.result.Result;
import com.interview.interview.dto.ExperienceDetailResponse;
import com.interview.interview.dto.ExperienceListResponse;
import com.interview.interview.service.ExperienceService;
import com.interview.common.utils.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 面经广场控制器
 * <p>
 * 提供公开面经的浏览接口，无需认证即可访问。
 * </p>
 */
@Tag(name = "面经广场模块")
@RestController
@RequestMapping("/experiences")
@RequiredArgsConstructor
public class ExperienceController {

    private final ExperienceService experienceService;

    /**
     * 面经广场列表（无需认证）
     *
     * @param page          页码
     * @param size          每页大小
     * @param keyword       标题关键词
     * @param positionTitle 职位筛选
     * @param sortBy        排序方式（publishedAt、viewCount、likeCount，默认按发布时间）
     * @return 面经列表（分页）
     */
    @Operation(summary = "面经广场列表（无需认证）")
    @GetMapping
    public Result<PageResult<ExperienceListResponse>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String positionTitle,
            @RequestParam(required = false) String sortBy) {
        return Result.success(experienceService.listPublic(page, size,
                keyword, positionTitle, sortBy));
    }

    /**
     * 面经详情（无需认证）
     *
     * @param id 面经 ID
     * @return 面经详情
     */
    @Operation(summary = "面经详情（无需认证）")
    @GetMapping("/{id}")
    public Result<ExperienceDetailResponse> getDetail(@PathVariable Long id) {
        return Result.success(experienceService.getDetail(id, SecurityUtils.getCurrentUserId()));
    }
}
