package com.interview.interview.service.impl;

import com.interview.interview.entity.Interview;
import com.interview.interview.entity.UserResume;
import com.interview.interview.mapper.InterviewMapper;
import com.interview.interview.mapper.UserResumeMapper;
import com.interview.interview.mapper.QuestionStandardCacheMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** 将单条清理放在独立 Spring Bean 中，保证调度器调用能经过事务代理。 */
@Service
@RequiredArgsConstructor
public class InterviewCleanupService {
    private final InterviewMapper interviewMapper;
    private final UserResumeMapper resumeMapper;
    private final QuestionStandardCacheMapper standardCacheMapper;

    @Value("${interview.file.upload-dir:./uploads}")
    private String uploadDir;

    @Transactional
    public void purgeInterview(Interview interview) {
        interviewMapper.physicallyDeleteNotes(interview.getId());
        interviewMapper.physicallyDeleteFavorites(interview.getId());
        interviewMapper.physicallyDeleteComments(interview.getId());
        interviewMapper.physicallyDeleteShares(interview.getId());
        interviewMapper.physicallyDeleteQuestions(interview.getId());
        standardCacheMapper.deletePersonalByInterview(interview.getId());
        interviewMapper.markPurged(interview.getId());
        deleteFile(interview.getAudioFilePath());
    }

    @Transactional
    public void purgeResume(UserResume resume) {
        if (resumeMapper.physicallyDelete(resume.getId()) > 0) deleteFile(resume.getFilePath());
    }

    private void deleteFile(String filename) {
        if (filename == null || filename.isBlank() || filename.contains("/") || filename.contains("\\")) return;
        Path directory = Path.of(uploadDir).toAbsolutePath().normalize();
        Path file = directory.resolve(filename).normalize();
        if (!file.startsWith(directory)) return;
        try { Files.deleteIfExists(file); }
        catch (IOException e) { throw new IllegalStateException("清理文件失败: " + filename, e); }
    }
}
