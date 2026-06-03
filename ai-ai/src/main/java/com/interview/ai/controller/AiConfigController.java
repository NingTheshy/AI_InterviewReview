package com.interview.ai.controller;

import com.interview.ai.dto.AiConfigRequest;
import com.interview.ai.dto.AiConfigResponse;
import com.interview.ai.entity.AiConfig;
import com.interview.ai.service.AiConfigService;
import com.interview.common.result.PageResult;
import com.interview.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * AI 配置管理控制器
 * <p>
 * 提供 AI 模型配置的 CRUD 接口，仅管理员可访问。
 * </p>
 */
@Tag(name = "AI 配置模块")
@RestController
@RequestMapping("/ai-configs")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AiConfigController {

    private final AiConfigService aiConfigService;

    @Operation(summary = "获取 AI 配置列表")
    @GetMapping
    public Result<PageResult<AiConfigResponse>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        PageResult<AiConfig> pageResult = aiConfigService.list(page, size);
        PageResult<AiConfigResponse> response = new PageResult<>(
                pageResult.getRecords().stream().map(this::toResponse).toList(),
                pageResult.getTotal(), pageResult.getPage(), pageResult.getSize());
        return Result.success(response);
    }

    @Operation(summary = "获取单个配置详情")
    @GetMapping("/{id}")
    public Result<AiConfigResponse> getDetail(@PathVariable Long id) {
        return Result.success(toResponse(aiConfigService.getDetail(id)));
    }

    @Operation(summary = "新增 AI 模型配置")
    @PostMapping
    public Result<Void> create(@Valid @RequestBody AiConfigRequest request) {
        AiConfig config = new AiConfig();
        config.setConfigName(request.getConfigName());
        config.setProvider(request.getProvider());
        config.setModelName(request.getModelName());
        config.setApiKey(request.getApiKey());
        config.setApiEndpoint(request.getApiEndpoint());
        config.setConfigType(request.getConfigType());
        config.setIsDefault(request.getIsDefault());
        config.setSortOrder(request.getSortOrder());
        aiConfigService.create(config);
        return Result.success();
    }

    @Operation(summary = "更新 AI 模型配置")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody AiConfigRequest request) {
        AiConfig config = new AiConfig();
        config.setConfigName(request.getConfigName());
        config.setProvider(request.getProvider());
        config.setModelName(request.getModelName());
        config.setApiKey(request.getApiKey());
        config.setApiEndpoint(request.getApiEndpoint());
        config.setConfigType(request.getConfigType());
        config.setIsDefault(request.getIsDefault());
        config.setSortOrder(request.getSortOrder());
        aiConfigService.update(id, config);
        return Result.success();
    }

    @Operation(summary = "删除 AI 模型配置")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        aiConfigService.delete(id);
        return Result.success();
    }

    @Operation(summary = "设为默认配置")
    @PutMapping("/{id}/default")
    public Result<Void> setDefault(@PathVariable Long id) {
        aiConfigService.setDefault(id);
        return Result.success();
    }

    private AiConfigResponse toResponse(AiConfig config) {
        return AiConfigResponse.builder()
                .id(config.getId())
                .configName(config.getConfigName())
                .provider(config.getProvider())
                .modelName(config.getModelName())
                .apiKey(AiConfigResponse.maskApiKey(config.getApiKey()))
                .apiEndpoint(config.getApiEndpoint())
                .configType(config.getConfigType())
                .isDefault(config.getIsDefault())
                .sortOrder(config.getSortOrder())
                .status(config.getStatus())
                .createdAt(config.getCreatedAt())
                .build();
    }
}
