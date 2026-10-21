package com.interview.ai.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.interview.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * AI 配置实体类
 * <p>
 * 对应 ai_config 表，存储 AI 模型的配置信息。
 * 支持多配置管理，可设置默认配置。
 * </p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_config")
public class AiConfig extends BaseEntity {

    private String configName;
    private String provider;
    private String modelName;
    private String apiKey;
    private String apiEndpoint;
    private Integer configType;
    private Integer isDefault;
    private Integer sortOrder;
    private Integer status;
    private Integer testStatus;
    private String testFingerprint;
    private LocalDateTime lastTestedAt;
    private String lastTestMessage;
}
