package com.interview.interview.service;

/**
 * 面试异步处理服务接口
 * <p>
 * 负责面试记录的异步 AI 处理流程，包括语音转文字、
 * 问题边界识别、评分等步骤。
 * </p>
 */
public interface InterviewAsyncService {

    /**
     * 异步处理面试记录
     * <p>
     * 处理步骤：
     * 1. 语音转文字（ASR）
     * 2. 问题边界识别
     * 3. AI 评分
     * 4. 整体评估
     * </p>
     *
     * @param interviewId 面试记录 ID
     */
    void processInterview(Long interviewId);
}
