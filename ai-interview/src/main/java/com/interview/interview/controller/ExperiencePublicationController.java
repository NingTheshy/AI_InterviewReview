package com.interview.interview.controller;

import com.interview.common.result.Result;
import com.interview.common.utils.SecurityUtils;
import com.interview.interview.dto.ExperiencePublicationRequest;
import com.interview.interview.dto.ExperiencePublicationResponse;
import com.interview.interview.service.ExperienceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** 当前用户管理一场面试的唯一公开面经。 */
@RestController
@RequestMapping("/interviews/{interviewId}/publication")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('experience:publish')")
public class ExperiencePublicationController {
    private final ExperienceService experienceService;

    @PostMapping
    public Result<ExperiencePublicationResponse> publish(@PathVariable Long interviewId,
            @Valid @RequestBody ExperiencePublicationRequest request) {
        return Result.success(experienceService.publish(interviewId, SecurityUtils.getCurrentUserId(), request.getSummaryMarkdown()));
    }

    @GetMapping
    public Result<ExperiencePublicationResponse> get(@PathVariable Long interviewId) {
        return Result.success(experienceService.getPublication(interviewId, SecurityUtils.getCurrentUserId()));
    }

    @PutMapping
    public Result<ExperiencePublicationResponse> update(@PathVariable Long interviewId,
            @Valid @RequestBody ExperiencePublicationRequest request) {
        return Result.success(experienceService.updatePublication(interviewId, SecurityUtils.getCurrentUserId(), request.getSummaryMarkdown()));
    }

    @DeleteMapping
    public Result<Void> withdraw(@PathVariable Long interviewId) {
        experienceService.withdraw(interviewId, SecurityUtils.getCurrentUserId());
        return Result.success();
    }
}
