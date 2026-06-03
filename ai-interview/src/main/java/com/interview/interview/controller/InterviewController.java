package com.interview.interview.controller;

import com.interview.common.result.PageResult;
import com.interview.common.result.Result;
import com.interview.common.utils.SecurityUtils;
import com.interview.interview.dto.InterviewDetailResponse;
import com.interview.interview.dto.InterviewListResponse;
import com.interview.interview.dto.InterviewStatusResponse;
import com.interview.interview.service.InterviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import jakarta.validation.constraints.NotBlank;

/**
 * 面试控制器
 * <p>
 * 提供面试记录的上传、查询、删除、重试等接口。
 * </p>
 */
@Tag(name = "面试模块")
@RestController
@RequestMapping("/interviews")
@RequiredArgsConstructor
public class InterviewController {

    private final InterviewService interviewService;

    /**
     * 上传面试（音频 + 简历 + JD）
     *
     * @param audioFile     音频文件
     * @param resumeFile    简历文件
     * @param jdText        职位描述文本
     * @param title         面试标题
     * @param companyName   公司名称
     * @param positionTitle 职位名称
     * @param industry      行业
     * @param interviewType 面试类型
     * @return 新建面试记录 ID
     */
    @Operation(summary = "上传面试")
    @PostMapping("/upload")
    public Result<Long> upload(
            @RequestParam("audioFile") MultipartFile audioFile,
            @RequestParam(value = "resumeFile") MultipartFile resumeFile,
            @RequestParam("jdText") @NotBlank(message = "岗位JD不能为空") String jdText,
            @RequestParam(value = "title", required = false) String title,
            @RequestParam(value = "companyName", required = false) String companyName,
            @RequestParam(value = "positionTitle", required = false) String positionTitle,
            @RequestParam(value = "industry", required = false) String industry,
            @RequestParam(value = "interviewType", required = false) String interviewType) {
        Long userId = SecurityUtils.getCurrentUserId();
        Long id = interviewService.upload(userId, audioFile, resumeFile, jdText,
                title, companyName, positionTitle, industry, interviewType);
        return Result.success(id);
    }

    /**
     * 获取当前用户的面试记录列表
     *
     * @param page          页码
     * @param size          每页大小
     * @param companyName   公司名称筛选
     * @param positionTitle 职位筛选
     * @param industry      行业筛选
     * @param interviewType 面试类型筛选
     * @return 面试记录列表（分页）
     */
    @Operation(summary = "获取面试记录列表")
    @GetMapping
    public Result<PageResult<InterviewListResponse>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String companyName,
            @RequestParam(required = false) String positionTitle,
            @RequestParam(required = false) String industry,
            @RequestParam(required = false) String interviewType,
            @RequestParam(defaultValue = "createdAt") String sortBy) {
        Long userId = SecurityUtils.getCurrentUserId();
        return Result.success(interviewService.list(userId, page, size,
                companyName, positionTitle, industry, interviewType, sortBy));
    }

    /**
     * 获取面试详情
     *
     * @param id 面试 ID
     * @return 面试详情（含问题列表和评分）
     */
    @Operation(summary = "获取面试详情")
    @GetMapping("/{id}")
    public Result<InterviewDetailResponse> getDetail(@PathVariable Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        return Result.success(interviewService.getDetail(id, userId));
    }

    /**
     * 删除面试记录（软删除）
     *
     * @param id 面试 ID
     * @return 操作结果
     */
    @Operation(summary = "删除面试记录")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        interviewService.delete(id, userId);
        return Result.success();
    }

    /**
     * 查询面试处理进度
     *
     * @param id 面试 ID
     * @return 面试处理进度信息
     */
    @Operation(summary = "查询处理进度")
    @GetMapping("/{id}/status")
    public Result<InterviewStatusResponse> getStatus(@PathVariable Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        return Result.success(interviewService.getStatus(id, userId));
    }

    /**
     * 重新处理失败的面试
     *
     * @param id 面试 ID
     * @return 操作结果
     */
    @Operation(summary = "重新处理失败的面试")
    @PostMapping("/{id}/retry")
    public Result<Void> retry(@PathVariable Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        interviewService.retry(id, userId);
        return Result.success();
    }
}
