package com.interview.interview.dto;

import lombok.Builder;
import lombok.Data;

/**
 * 面试处理进度响应
 * <p>
 * 对应需求文档 4.3 节"处理进度响应"，包含当前状态、处理步骤和进度百分比。
 * </p>
 */
@Data
@Builder
public class InterviewStatusResponse {

    /** 处理状态：0=处理中, 1=已完成, 2=失败 */
    private Integer status;

    /** 处理步骤：0=未开始, 1=语音转文字, 2=问题识别, 3=逐题评分, 4=整体评分 */
    private Integer processingStep;

    /** 处理步骤名称 */
    private String processingStepName;

    /** 进度百分比 (0-100) */
    private Integer progress;
}
