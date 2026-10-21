package com.interview.interview.service;

/** 简历后台解析任务。 */
public interface ResumeAsyncService {
    void parse(Long resumeId, String processingToken);
}
