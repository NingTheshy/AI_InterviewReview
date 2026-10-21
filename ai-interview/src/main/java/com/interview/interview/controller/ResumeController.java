package com.interview.interview.controller;

import com.interview.common.result.Result;
import com.interview.common.utils.SecurityUtils;
import com.interview.interview.dto.ResumeResponse;
import com.interview.interview.service.ResumeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/** 当前用户的可复用简历库。 */
@Tag(name = "简历模块")
@RestController
@RequestMapping("/resumes")
@RequiredArgsConstructor
public class ResumeController {

    private final ResumeService resumeService;

    @Operation(summary = "上传并解析一份新简历版本")
    @PostMapping
    @PreAuthorize("hasAuthority('interview:create')")
    public Result<ResumeResponse> upload(@RequestParam("file") MultipartFile file) {
        return Result.success(ResumeResponse.from(
                resumeService.upload(SecurityUtils.getCurrentUserId(), file)));
    }

    @Operation(summary = "获取当前用户可用于新面试的简历")
    @GetMapping
    @PreAuthorize("hasAuthority('interview:read')")
    public Result<List<ResumeResponse>> list() {
        return Result.success(resumeService.list(SecurityUtils.getCurrentUserId()).stream()
                .map(ResumeResponse::from).toList());
    }

    @Operation(summary = "从简历库隐藏简历")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('interview:manage')")
    public Result<Void> delete(@PathVariable Long id) {
        resumeService.delete(id, SecurityUtils.getCurrentUserId());
        return Result.success();
    }
}
