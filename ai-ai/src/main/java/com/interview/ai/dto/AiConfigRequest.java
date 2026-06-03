package com.interview.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * AI 配置请求 DTO
 */
@Data
public class AiConfigRequest {

    @NotBlank(message = "配置名称不能为空")
    @Size(max = 50, message = "配置名称长度不能超过 50 个字符")
    private String configName;

    @NotBlank(message = "供应商不能为空")
    private String provider;

    @NotBlank(message = "模型名称不能为空")
    private String modelName;

    @NotBlank(message = "API Key不能为空")
    @Size(max = 500, message = "API Key长度不能超过500")
    private String apiKey;

    @Size(max = 500, message = "API端点长度不能超过500")
    private String apiEndpoint;

    private Integer configType;

    private Integer isDefault;

    private Integer sortOrder;
}
