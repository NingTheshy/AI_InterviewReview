package com.interview.interview.service.impl;

import com.interview.ai.service.CompanyClassifier;
import com.interview.ai.service.ScoringService;
import com.interview.ai.service.TranscriptionService;
import com.interview.common.constant.CompanyTier;
import com.interview.common.constant.ErrorCode;
import com.interview.common.constant.InterviewStatus;
import com.interview.common.exception.BusinessException;
import com.interview.interview.entity.Interview;
import com.interview.interview.mapper.InterviewMapper;
import com.interview.interview.service.InterviewAsyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.nio.file.Paths;

@Slf4j
@Service
@RequiredArgsConstructor
public class InterviewAsyncServiceImpl implements InterviewAsyncService {

    private final InterviewMapper interviewMapper;
    private final TranscriptionService transcriptionService;
    private final ScoringService scoringService;
    private final CompanyClassifier companyClassifier;
    private final ScoreResultParser scoreResultParser;

    @Value("${interview.file.upload-dir:./uploads}")
    private String uploadDir;

    @Override
    @Async("asyncExecutor")
    public void processInterview(Long interviewId) {
        log.info("开始异步处理面试: id={}", interviewId);
        Interview interview = interviewMapper.selectById(interviewId);
        if (interview == null) {
            log.error("面试记录不存在: id={}", interviewId);
            return;
        }
        try {
            // 步骤 1: 语音转文字
            updateStep(interview, 1);
            String audioPath = Paths.get(uploadDir, interview.getAudioFilePath()).toString();
            String transcript = transcriptionService.transcribe(audioPath, null);
            if (transcript == null || transcript.isBlank()) {
                throw new BusinessException(ErrorCode.PROCESSING_FAILED.getCode(),
                        "语音转文字结果为空，请检查音频文件");
            }
            interview.setTranscriptText(transcript);
            interviewMapper.updateById(interview);
            log.info("语音转文字完成: interviewId={}, 长度={}", interviewId, transcript.length());

            // 步骤 1.5: 公司分级
            CompanyTier tier = companyClassifier.classify(
                    interview.getCompanyName(),
                    interview.getIndustry(),
                    interview.getJdText()
            );
            interview.setCompanyTier(tier.getCode());
            interviewMapper.updateById(interview);
            log.info("公司分级完成: interviewId={}, company={}, tier={}", interviewId, interview.getCompanyName(), tier.getName());

            // 步骤 2: 问题边界识别 + 分批评分
            updateStep(interview, 2);
            String jdText = interview.getJdText();
            String resumeText = interview.getResumeText();
            String scoreResult = scoringService.analyzeAndScoreBatch(transcript, jdText, resumeText, null, tier.getCode());

            // 步骤 3: 解析评分结果
            updateStep(interview, 3);
            scoreResultParser.parseAndSave(interview, scoreResult);

            // 步骤 4: 完成
            updateStep(interview, 4);
            interview.setStatus(InterviewStatus.COMPLETED.getCode());
            interviewMapper.updateById(interview);
            log.info("面试处理完成: id={}", interviewId);
        } catch (Exception e) {
            log.error("面试处理失败: id={}", interviewId, e);
            interview.setStatus(InterviewStatus.FAILED.getCode());
            interview.setErrorMessage(e.getMessage());
            interviewMapper.updateById(interview);
        }
    }

    private void updateStep(Interview interview, int step) {
        interview.setProcessingStep(step);
        interviewMapper.updateById(interview);
    }
}
