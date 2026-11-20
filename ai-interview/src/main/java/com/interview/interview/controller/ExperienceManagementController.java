package com.interview.interview.controller;

import com.interview.common.result.PageResult;
import com.interview.common.result.Result;
import com.interview.common.utils.SecurityUtils;
import com.interview.interview.dto.*;
import com.interview.interview.service.ExperienceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** 本人面经管理与登录后的面经互动。 */
@RestController
@RequiredArgsConstructor
public class ExperienceManagementController {
    private final ExperienceService service;

    @GetMapping("/me/experiences")
    @PreAuthorize("hasAuthority('experience:publish')")
    public Result<PageResult<ExperiencePublicationResponse>> mine(@RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return Result.success(service.mine(SecurityUtils.getCurrentUserId(), page, size));
    }

    @GetMapping("/me/experiences/{id}")
    @PreAuthorize("hasAuthority('experience:publish')")
    public Result<ExperienceEditorResponse> editor(@PathVariable Long id) {
        return Result.success(service.editPublished(id, SecurityUtils.getCurrentUserId()));
    }

    @PutMapping("/me/experiences/{id}")
    @PreAuthorize("hasAuthority('experience:publish')")
    public Result<ExperiencePublicationResponse> update(@PathVariable Long id,
            @Valid @RequestBody ExperiencePublicationRequest request) {
        return Result.success(service.updatePublication(id, SecurityUtils.getCurrentUserId(), request));
    }

    @DeleteMapping("/me/experiences/{id}")
    @PreAuthorize("hasAuthority('experience:publish')")
    public Result<Void> delete(@PathVariable Long id) {
        service.deletePublication(id, SecurityUtils.getCurrentUserId());
        return Result.success();
    }

    @GetMapping("/me/experience-favorites")
    @PreAuthorize("hasAuthority('experience:interact')")
    public Result<PageResult<ExperienceListResponse>> favorites(@RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return Result.success(service.favorites(SecurityUtils.getCurrentUserId(), page, size));
    }

    @PostMapping("/experiences/{id}/like")
    @PreAuthorize("hasAuthority('experience:interact')")
    public Result<Void> like(@PathVariable Long id) {
        service.like(id, SecurityUtils.getCurrentUserId(), true);
        return Result.success();
    }

    @DeleteMapping("/experiences/{id}/like")
    @PreAuthorize("hasAuthority('experience:interact')")
    public Result<Void> unlike(@PathVariable Long id) {
        service.like(id, SecurityUtils.getCurrentUserId(), false);
        return Result.success();
    }

    @PostMapping("/experiences/{id}/favorite")
    @PreAuthorize("hasAuthority('experience:interact')")
    public Result<Void> favorite(@PathVariable Long id) {
        service.favorite(id, SecurityUtils.getCurrentUserId(), true);
        return Result.success();
    }

    @DeleteMapping("/experiences/{id}/favorite")
    @PreAuthorize("hasAuthority('experience:interact')")
    public Result<Void> unfavorite(@PathVariable Long id) {
        service.favorite(id, SecurityUtils.getCurrentUserId(), false);
        return Result.success();
    }
}
