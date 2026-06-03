package com.interview.common.constant;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 面试处理步骤枚举
 * <p>
 * 定义异步处理流程中的各个步骤，用于进度追踪和前端展示。
 * </p>
 */
@Getter
@AllArgsConstructor
public enum ProcessingStep {

    NOT_STARTED(0, "未开始"),
    SPEECH_TO_TEXT(1, "语音转文字"),
    QUESTION_BOUNDARY(2, "问题边界识别"),
    SCORING(3, "逐题评分"),
    OVERALL_SCORING(4, "整体评分");

    private final int code;
    private final String description;
}
