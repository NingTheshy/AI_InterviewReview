package com.interview.common.constant;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * AI 配置类型枚举
 * <p>
 * 区分不同类型的 AI 模型配置，用于动态选择 ASR 或 LLM 客户端。
 * </p>
 */
@Getter
@AllArgsConstructor
public enum ConfigType {

    ASR(1, "语音转文字"),
    LLM(2, "文本分析评分");

    private final int code;
    private final String description;
}
