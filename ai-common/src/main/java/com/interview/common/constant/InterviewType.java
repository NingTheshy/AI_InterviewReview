package com.interview.common.constant;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 面试类型枚举
 * <p>
 * 定义支持的面试类型，用于筛选和分类面试记录。
 * </p>
 */
@Getter
@AllArgsConstructor
public enum InterviewType {

    CODING("coding", "技术面"),
    BEHAVIORAL("behavioral", "行为面"),
    SYSTEM_DESIGN("system_design", "系统设计"),
    COMPREHENSIVE("comprehensive", "综合");

    private final String code;
    private final String description;
}
