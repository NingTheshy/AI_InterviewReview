package com.interview.interview.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.interview.ai.dto.TranscriptionResult;
import com.interview.ai.service.CompanyClassifier;
import com.interview.ai.service.ScoringService;
import com.interview.ai.service.TranscriptionService;
import com.interview.common.constant.CompanyTier;
import com.interview.common.constant.ErrorCode;
import com.interview.common.constant.InterviewStatus;
import com.interview.common.exception.BusinessException;
import com.interview.interview.entity.Interview;
import com.interview.interview.entity.InterviewQuestion;
import com.interview.interview.mapper.InterviewMapper;
import com.interview.interview.mapper.InterviewQuestionMapper;
import com.interview.interview.service.InterviewAsyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.nio.file.Paths;
import java.util.List;

/**
 * 面试复盘的异步编排器。
 *
 * <p>该类只负责推进状态机和持久化各阶段产物，不在此处实现具体模型策略。处理顺序为：Fun-ASR
 * 结构化转写、公司分级、标准答案与逐项比对、评分结果落库。任一阶段失败都会将面试标记为失败，
 * 以便前端轮询状态并允许用户重试。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InterviewAsyncServiceImpl implements InterviewAsyncService {

    private final InterviewMapper interviewMapper;
    private final InterviewQuestionMapper questionMapper;
    private final TranscriptionService transcriptionService;
    private final ScoringService scoringService;
    private final CompanyClassifier companyClassifier;
    private final ScoreResultParser scoreResultParser;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${interview.file.upload-dir:./uploads}")
    private String uploadDir;

    /**
     * 在独立线程中执行一场面试的完整复盘流程。
     *
     * <p>每个处理阶段先更新 {@code processingStep}，再执行耗时操作，前端因而能够得到真实进度。
     * 转写文本、句子级 JSON 和音频时长须作为同一阶段产物保存；评分阶段使用带原始时间戳和
     * speakerId 的文本，避免模型虚构定位信息。</p>
     *
     * @param interviewId 待处理的面试记录 ID
     */
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
            // 步骤 1: FunASR 转写、句子时间戳和原始说话人标识
            updateStep(interview, 1);
            String audioPath = Paths.get(uploadDir, interview.getAudioFilePath()).toString();
            TranscriptionResult transcription = transcriptionService.transcribeDetailed(audioPath, null);
            String transcript = transcription.text();
            if (transcript == null || transcript.isBlank()) {
                throw new BusinessException(ErrorCode.PROCESSING_FAILED.getCode(),
                        "语音转文字结果为空，请检查音频文件");
            }
            interview.setTranscriptText(transcript);
            interview.setTranscriptSegments(objectMapper.writeValueAsString(transcription.sentences()));
            interview.setAudioDuration(transcription.audioDurationSeconds());
            interviewMapper.updateById(interview);
            log.info("语音转文字完成: interviewId={}, 长度={}, 句子数={}",
                    interviewId, transcript.length(), transcription.sentences().size());

            // 步骤 1.5: 公司分级
            CompanyTier tier = companyClassifier.classify(
                    interview.getCompanyName(),
                    interview.getIndustry(),
                    interview.getJdText()
            );
            interview.setCompanyTier(tier.getCode());
            interviewMapper.updateById(interview);
            log.info("公司分级完成: interviewId={}, company={}, tier={}", interviewId, interview.getCompanyName(), tier.getName());

            // 步骤 2: 问答识别、满分标准生成、逐项比对与服务端计分
            updateStep(interview, 2);
            String jdText = interview.getJdText();
            String resumeText = interview.getResumeText();
            List<ScoringService.SavedRubric> savedRubrics = questionMapper.selectList(
                            new LambdaQueryWrapper<InterviewQuestion>()
                                    .eq(InterviewQuestion::getInterviewId, interviewId))
                    .stream()
                    .map(question -> new ScoringService.SavedRubric(question.getQuestionText(),
                            question.getReferenceAnswer(), question.getRubricDetail()))
                    .toList();
            String scoreResult = scoringService.analyzeWithRubric(transcription.toScoringTranscript(), jdText, resumeText,
                    interview.getPositionTitle(), interview.getIndustry(), tier.getCode(), null, savedRubrics);

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

    /** 持久化当前阶段，作为轮询接口的唯一进度来源。 */
    private void updateStep(Interview interview, int step) {
        interview.setProcessingStep(step);
        interviewMapper.updateById(interview);
    }
}
