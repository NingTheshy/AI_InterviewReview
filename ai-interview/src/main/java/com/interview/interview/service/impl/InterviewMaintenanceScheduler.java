package com.interview.interview.service.impl;

import com.interview.interview.entity.Interview;
import com.interview.interview.mapper.InterviewMapper;
import com.interview.interview.service.InterviewAsyncService;
import com.interview.interview.service.ResumeAsyncService;
import com.interview.interview.mapper.UserResumeMapper;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 恢复中断任务并清理超过七天恢复期的面试敏感数据。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InterviewMaintenanceScheduler {

    private final InterviewMapper interviewMapper;
    private final InterviewAsyncService interviewAsyncService;
    private final ResumeAsyncService resumeAsyncService;
    private final UserResumeMapper resumeMapper;
    private final InterviewCleanupService cleanupService;

    @EventListener(ApplicationReadyEvent.class)
    public void recoverInterruptedTasks() {
        for (Interview interview : interviewMapper.selectRecoverable()) {
            try {
                interviewAsyncService.processInterview(interview.getId());
            } catch (RuntimeException e) {
                log.error("恢复面试任务提交失败: interviewId={}", interview.getId(), e);
            }
        }
        resumeMapper.selectRecoverable().forEach(resume -> {
            String token = UUID.randomUUID().toString();
            if (resumeMapper.claimParse(resume.getId(), token, LocalDateTime.now().plusMinutes(5)) > 0) {
                try {
                    resumeAsyncService.parse(resume.getId(), token);
                } catch (RuntimeException e) {
                    log.error("恢复简历解析任务提交失败: resumeId={}", resume.getId(), e);
                    resumeMapper.failParse(resume.getId(), token, "系统任务队列繁忙，请稍后重试");
                }
            }
        });
    }

    @Scheduled(fixedDelayString = "${interview.retention.cleanup-interval-ms:3600000}")
    public void purgeExpiredInterviews() {
        for (Interview interview : interviewMapper.selectExpiredDeleted()) {
            cleanupService.purgeInterview(interview);
        }
        resumeMapper.selectHiddenUnreferenced().forEach(cleanupService::purgeResume);
    }
}
