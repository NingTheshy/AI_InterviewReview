package com.interview.interview.service.impl;

import com.interview.common.utils.PdfParser;
import com.interview.interview.entity.UserResume;
import com.interview.interview.mapper.UserResumeMapper;
import com.interview.interview.service.ResumeAsyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.UUID;

/** 使用租约和令牌保护的简历异步解析器。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ResumeAsyncServiceImpl implements ResumeAsyncService {
    private final UserResumeMapper resumeMapper;

    @Value("${interview.file.upload-dir:./uploads}")
    private String uploadDir;

    @Override
    @Async("asyncExecutor")
    public void parse(Long resumeId, String processingToken) {
        UserResume resume = resumeMapper.selectById(resumeId);
        if (resume == null || processingToken == null || !processingToken.equals(resume.getProcessingToken())) {
            return;
        }
        try {
            String text = PdfParser.extractText(Path.of(uploadDir, resume.getFilePath()).toFile());
            if (text == null || text.isBlank()) {
                throw new IllegalArgumentException("简历未解析出有效文本，请更换PDF文件");
            }
            if (resumeMapper.ownsParse(resumeId, processingToken) == 0) return;
            resume.setParsedText(text.trim());
            resume.setParseStatus(UserResume.PARSE_SUCCESS);
            resume.setParseError(null);
            resume.setProcessingToken(null);
            resume.setLeaseExpiresAt(null);
            resume.setHeartbeatAt(LocalDateTime.now());
            resumeMapper.updateById(resume);
        } catch (Exception e) {
            log.warn("简历异步解析失败: resumeId={}", resumeId, e);
            if (resumeMapper.ownsParse(resumeId, processingToken) > 0) {
                resume.setParseStatus(UserResume.PARSE_FAILED);
                resume.setParseError(limit(e.getMessage()));
                resume.setProcessingToken(null);
                resume.setLeaseExpiresAt(null);
                resume.setHeartbeatAt(LocalDateTime.now());
                resumeMapper.updateById(resume);
            }
        }
    }

    private String limit(String message) {
        String value = message == null || message.isBlank() ? "简历解析失败" : message;
        return value.length() > 500 ? value.substring(0, 500) : value;
    }
}
