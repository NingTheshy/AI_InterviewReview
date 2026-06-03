package com.interview.ai.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 配置响应 DTO
 * <p>
 * 隐藏 apiKey 敏感信息，仅返回后 4 位。
 * </p>
 */
@Data
@Builder
public class AiConfigResponse {

    private Long id;
    private String configName;
    private String provider;
    private String modelName;
    private String apiKey;
    private String apiEndpoint;
    private Integer configType;
    private Integer isDefault;
    private Integer sortOrder;
    private Integer status;
    private LocalDateTime createdAt;

    /**
     * 将完整 apiKey 脱敏为 ****xxxx 格式
     */
    public static String maskApiKey(String apiKey) {
        if (apiKey == null || apiKey.length() <= 4) {
            return "****";
        }
        return "****" + apiKey.substring(apiKey.length() - 4);
    }
}
