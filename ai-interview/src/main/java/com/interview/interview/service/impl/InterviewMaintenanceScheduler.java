package com.interview.interview.service.impl;

import com.interview.interview.entity.Interview;
import com.interview.interview.mapper.InterviewMapper;
import com.interview.interview.service.InterviewAsyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** 恢复中断任务并清理超过七天恢复期的面试敏感数据。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InterviewMaintenanceScheduler {

    private final InterviewMapper interviewMapper;
    private final InterviewAsyncService interviewAsyncService;

    @Value("${interview.file.upload-dir:./uploads}")
    private String uploadDir;

    @EventListener(ApplicationReadyEvent.class)
    public void recoverInterruptedTasks() {
        for (Interview interview : interviewMapper.selectRecoverable()) {
            try {
                interviewAsyncService.processInterview(interview.getId());
            } catch (RuntimeException e) {
                log.error("恢复面试任务提交失败: interviewId={}", interview.getId(), e);
            }
        }
    }

    @Scheduled(fixedDelayString = "${interview.retention.cleanup-interval-ms:3600000}")
    public void purgeExpiredInterviews() {
        for (Interview interview : interviewMapper.selectExpiredDeleted()) {
            purgeOne(interview);
        }
    }

    @Transactional
    protected void purgeOne(Interview interview) {
        deleteAudio(interview.getAudioFilePath());
        interviewMapper.physicallyDeleteNotes(interview.getId());
        interviewMapper.physicallyDeleteFavorites(interview.getId());
        interviewMapper.physicallyDeleteComments(interview.getId());
        interviewMapper.physicallyDeleteShares(interview.getId());
        interviewMapper.physicallyDeleteQuestions(interview.getId());
        interviewMapper.markPurged(interview.getId());
        log.info("已清理过期面试敏感数据: interviewId={}", interview.getId());
    }

    private void deleteAudio(String filename) {
        if (filename == null || filename.isBlank() || filename.contains("/") || filename.contains("\\")) return;
        Path directory = Path.of(uploadDir).toAbsolutePath().normalize();
        Path file = directory.resolve(filename).normalize();
        if (!file.startsWith(directory)) return;
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            throw new IllegalStateException("清理面试音频失败: " + filename, e);
        }
    }
}
