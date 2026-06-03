package com.interview.common.constant;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 面试处理状态枚举
 * <p>
 * 表示面试记录的处理进度状态，用于异步处理流程的状态流转。
 * </p>
 */
@Getter
@AllArgsConstructor
public enum InterviewStatus {

    PROCESSING(0, "处理中"),
    COMPLETED(1, "已完成"),
    FAILED(2, "失败");

    private final int code;
    private final String description;

    public static InterviewStatus fromCode(int code) {
        for (InterviewStatus status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        throw new IllegalArgumentException("未知的面试状态: " + code);
    }
}
