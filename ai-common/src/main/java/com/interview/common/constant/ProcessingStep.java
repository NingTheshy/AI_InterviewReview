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
    SPEECH_TO_TEXT(1, "FunASR转写与说话人识别"),
    QUESTION_BOUNDARY(2, "标准答案生成与逐项比对"),
    SCORING(3, "服务端计分与结果保存"),
    OVERALL_SCORING(4, "整体评分与总结");

    private final int code;
    private final String description;
}
