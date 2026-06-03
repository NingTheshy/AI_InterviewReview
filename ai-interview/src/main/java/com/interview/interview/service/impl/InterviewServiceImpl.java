package com.interview.interview.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.interview.common.constant.ErrorCode;
import com.interview.common.constant.InterviewStatus;
import com.interview.common.exception.BusinessException;
import com.interview.common.result.PageResult;
import com.interview.common.utils.PdfParser;
import com.interview.interview.dto.InterviewDetailResponse;
import com.interview.interview.dto.InterviewListResponse;
import com.interview.interview.dto.InterviewStatusResponse;
import com.interview.interview.entity.Interview;
import com.interview.interview.entity.InterviewQuestion;
import com.interview.interview.mapper.InterviewMapper;
import com.interview.interview.mapper.InterviewQuestionMapper;
import com.interview.interview.service.InterviewAsyncService;
import com.interview.interview.service.InterviewService;
import com.interview.common.utils.FileUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 面试服务实现类
 * <p>
 * 提供面试记录的完整生命周期管理：
 * <ul>
 *   <li>upload - 上传面试材料并触发异步处理</li>
 *   <li>list - 分页查询用户面试记录</li>
 *   <li>getDetail - 获取面试详情（含权限校验）</li>
 *   <li>delete - 软删除面试记录</li>
 *   <li>getStatus - 查询处理进度</li>
 *   <li>retry - 重新处理失败的面试</li>
 * </ul>
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InterviewServiceImpl implements InterviewService {

    private final InterviewMapper interviewMapper;
    private final InterviewQuestionMapper questionMapper;
    private final InterviewAsyncService interviewAsyncService;

    @Value("${interview.file.upload-dir:./uploads}")
    private String uploadDir;

    @Value("${interview.file.base-url:/files}")
    private String baseUrl;

    /**
     * {@inheritDoc}
     * <p>
     * 实现逻辑：
     * 1. 构建面试实体，设置初始状态为 UPLOADED
     * 2. 保存到数据库
     * 3. 触发异步 AI 处理流程
     * </p>
     */
    @Override
    public Long upload(Long userId, MultipartFile audioFile, MultipartFile resumeFile,
                       String jdText, String title, String companyName,
                       String positionTitle, String industry, String interviewType) {
        FileUtil.validateAudioFile(audioFile);

        String audioFileName = FileUtil.generateFileName(audioFile.getOriginalFilename());
        try {
            Path audioPath = FileUtil.saveFile(audioFile, uploadDir, audioFileName);
        } catch (IOException e) {
            log.error("保存音频文件失败", e);
            throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(), "文件保存失败");
        }

        String resumeFileName = null;
        String resumeText = null;
        FileUtil.validateResumeFile(resumeFile);
        resumeFileName = FileUtil.generateFileName(resumeFile.getOriginalFilename());
        try {
            FileUtil.saveFile(resumeFile, uploadDir, resumeFileName);
        } catch (IOException e) {
            log.error("保存简历文件失败", e);
            throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(), "文件保存失败");
        }

        // 解析 PDF 提取文本
        try {
            resumeText = PdfParser.extractText(resumeFile.getInputStream());
            log.info("简历 PDF 解析完成: 文件={}, 文本长度={}", resumeFileName, resumeText.length());
        } catch (IOException e) {
            log.warn("简历 PDF 解析失败，将使用空文本: 文件={}", resumeFileName, e);
            resumeText = "";
        }

        Interview interview = new Interview();
        interview.setUserId(userId);
        interview.setTitle(title);
        interview.setCompanyName(companyName);
        interview.setPositionTitle(positionTitle);
        interview.setIndustry(industry);
        interview.setInterviewType(interviewType);
        interview.setJdText(jdText);
        interview.setAudioFilePath(audioFileName);
        interview.setAudioFileSize(audioFile.getSize());
        interview.setResumeFilePath(resumeFileName);
        interview.setResumeText(resumeText);
        interview.setStatus(InterviewStatus.PROCESSING.getCode());
        interview.setProcessingStep(0);

        interviewMapper.insert(interview);

        // 异步处理面试
        interviewAsyncService.processInterview(interview.getId());

        log.info("面试记录创建成功: id={}, title={}", interview.getId(), title);
        return interview.getId();
    }

    /**
     * {@inheritDoc}
     * <p>
     * 实现逻辑：
     * 1. 按用户 ID 过滤
     * 2. 支持公司名称、职位、行业、面试类型的组合筛选
     * 3. 默认按创建时间倒序排列
     * 4. 转换为 InterviewListResponse，不返回敏感数据
     * </p>
     */
    @Override
    public PageResult<InterviewListResponse> list(Long userId, int page, int size,
                                                   String companyName, String positionTitle,
                                                   String industry, String interviewType,
                                                   String sortBy) {
        Page<Interview> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<Interview> wrapper = new LambdaQueryWrapper<>();

        wrapper.eq(Interview::getUserId, userId);

        if (companyName != null && !companyName.isEmpty()) {
            wrapper.like(Interview::getCompanyName, companyName);
        }
        if (positionTitle != null && !positionTitle.isEmpty()) {
            wrapper.like(Interview::getPositionTitle, positionTitle);
        }
        if (industry != null && !industry.isEmpty()) {
            wrapper.eq(Interview::getIndustry, industry);
        }
        if (interviewType != null && !interviewType.isEmpty()) {
            wrapper.eq(Interview::getInterviewType, interviewType);
        }

        // 排序
        if ("overallScore".equals(sortBy)) {
            wrapper.orderByDesc(Interview::getOverallScore);
        } else {
            wrapper.orderByDesc(Interview::getCreatedAt);
        }

        Page<Interview> result = interviewMapper.selectPage(pageParam, wrapper);

        List<InterviewListResponse> records = result.getRecords().stream()
                .map(this::toInterviewListResponse)
                .collect(Collectors.toList());

        return new PageResult<>(records, result.getTotal(), page, size);
    }

    /**
     * {@inheritDoc}
     * <p>
     * 实现逻辑：
     * 1. 校验面试存在性及用户所有权
     * 2. 查询关联的问题列表
     * 3. 转换为 InterviewDetailResponse
     * </p>
     */
    @Override
    public InterviewDetailResponse getDetail(Long id, Long userId) {
        Interview interview = interviewMapper.selectById(id);
        if (interview == null) {
            throw new BusinessException(ErrorCode.INTERVIEW_NOT_FOUND.getCode(),
                    ErrorCode.INTERVIEW_NOT_FOUND.getMessage());
        }
        if (!interview.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.INTERVIEW_ACCESS_DENIED.getCode(),
                    ErrorCode.INTERVIEW_ACCESS_DENIED.getMessage());
        }

        // 查询问题列表
        LambdaQueryWrapper<InterviewQuestion> questionWrapper = new LambdaQueryWrapper<>();
        questionWrapper.eq(InterviewQuestion::getInterviewId, id);
        questionWrapper.orderByAsc(InterviewQuestion::getQuestionIndex);
        List<InterviewQuestion> questions = questionMapper.selectList(questionWrapper);

        return toInterviewDetailResponse(interview, questions);
    }

    /**
     * {@inheritDoc}
     * <p>
     * 实现逻辑：校验面试存在性及用户所有权后执行软删除。
     * </p>
     */
    @Override
    public void delete(Long id, Long userId) {
        Interview interview = interviewMapper.selectById(id);
        if (interview == null) {
            throw new BusinessException(ErrorCode.INTERVIEW_NOT_FOUND.getCode(),
                    ErrorCode.INTERVIEW_NOT_FOUND.getMessage());
        }
        if (!interview.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.INTERVIEW_ACCESS_DENIED.getCode(),
                    ErrorCode.INTERVIEW_ACCESS_DENIED.getMessage());
        }

        interviewMapper.deleteById(id);
        log.info("删除面试记录: id={}, userId={}", id, userId);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public InterviewStatusResponse getStatus(Long id, Long userId) {
        Interview interview = interviewMapper.selectById(id);
        if (interview == null) {
            throw new BusinessException(ErrorCode.INTERVIEW_NOT_FOUND.getCode(),
                    ErrorCode.INTERVIEW_NOT_FOUND.getMessage());
        }
        if (!interview.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.INTERVIEW_ACCESS_DENIED.getCode(),
                    ErrorCode.INTERVIEW_ACCESS_DENIED.getMessage());
        }

        // 计算进度百分比
        int progress = calculateProgress(interview.getStatus(), interview.getProcessingStep());

        return InterviewStatusResponse.builder()
                .status(interview.getStatus())
                .processingStep(interview.getProcessingStep())
                .processingStepName(getStepName(interview.getProcessingStep()))
                .progress(progress)
                .build();
    }

    /**
     * 根据处理步骤计算进度百分比
     */
    private int calculateProgress(Integer status, Integer step) {
        if (status == null || step == null) return 0;

        // 已完成或失败
        if (status == 1) return 100; // COMPLETED
        if (status == 2) return 100; // FAILED

        // 处理中，根据步骤计算
        return switch (step) {
            case 0 -> 0;   // 未开始
            case 1 -> 25;  // 语音转文字
            case 2 -> 50;  // 问题识别
            case 3 -> 75;  // 逐题评分
            case 4 -> 90;  // 整体评分（接近完成）
            default -> 0;
        };
    }

    /**
     * 获取处理步骤名称
     */
    private String getStepName(Integer step) {
        if (step == null) return "未开始";
        return switch (step) {
            case 0 -> "未开始";
            case 1 -> "语音转文字";
            case 2 -> "问题边界识别";
            case 3 -> "逐题评分";
            case 4 -> "整体评分";
            default -> "未知步骤";
        };
    }

    /**
     * {@inheritDoc}
     * <p>
     * 实现逻辑：
     * 1. 校验面试存在性及用户所有权
     * 2. 重置状态为 UPLOADED，处理步骤归零
     * 3. 重新触发异步处理
     * </p>
     */
    @Override
    public void retry(Long id, Long userId) {
        Interview interview = interviewMapper.selectById(id);
        if (interview == null) {
            throw new BusinessException(ErrorCode.INTERVIEW_NOT_FOUND.getCode(),
                    ErrorCode.INTERVIEW_NOT_FOUND.getMessage());
        }
        if (!interview.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.INTERVIEW_ACCESS_DENIED.getCode(),
                    ErrorCode.INTERVIEW_ACCESS_DENIED.getMessage());
        }
        if (interview.getStatus() != InterviewStatus.FAILED.getCode()) {
            throw new BusinessException(ErrorCode.INTERVIEW_PROCESSING.getCode(),
                    "仅支持重试失败的面试记录");
        }

        // 重置状态
        interview.setStatus(InterviewStatus.PROCESSING.getCode());
        interview.setProcessingStep(0);
        interviewMapper.updateById(interview);

        // 重新异步处理
        interviewAsyncService.processInterview(id);

        log.info("重试面试处理: id={}", id);
    }

    // ==================== 私有方法 ====================

    /**
     * 将 Interview 实体转换为 InterviewListResponse
     *
     * @param interview 面试实体
     * @return 面试列表响应
     */
    private InterviewListResponse toInterviewListResponse(Interview interview) {
        return InterviewListResponse.builder()
                .id(interview.getId())
                .title(interview.getTitle())
                .companyName(interview.getCompanyName())
                .positionTitle(interview.getPositionTitle())
                .industry(interview.getIndustry())
                .interviewType(interview.getInterviewType())
                .status(interview.getStatus())
                .overallScore(interview.getOverallScore())
                .audioDuration(interview.getAudioDuration())
                .createdAt(interview.getCreatedAt())
                .build();
    }

    /**
     * 将 Interview 实体和问题列表转换为 InterviewDetailResponse
     *
     * @param interview 面试实体
     * @param questions 问题列表
     * @return 面试详情响应
     */
    private InterviewDetailResponse toInterviewDetailResponse(Interview interview,
                                                              List<InterviewQuestion> questions) {
        List<InterviewDetailResponse.QuestionResponse> questionResponses = questions.stream()
                .map(q -> InterviewDetailResponse.QuestionResponse.builder()
                        .id(q.getId())
                        .questionIndex(q.getQuestionIndex())
                        .questionText(q.getQuestionText())
                        .answerText(q.getAnswerText())
                        .score(q.getScore())
                        .dimensionContent(q.getDimensionContent())
                        .dimensionLogic(q.getDimensionLogic())
                        .dimensionExpression(q.getDimensionExpression())
                        .dimensionProfessional(q.getDimensionProfessional())
                        .improvementTip(q.getImprovementTip())
                        .referenceAnswer(q.getReferenceAnswer())
                        .build())
                .collect(Collectors.toList());

        return InterviewDetailResponse.builder()
                .id(interview.getId())
                .title(interview.getTitle())
                .companyName(interview.getCompanyName())
                .positionTitle(interview.getPositionTitle())
                .industry(interview.getIndustry())
                .interviewType(interview.getInterviewType())
                .status(interview.getStatus())
                .processingStep(interview.getProcessingStep())
                .audioFilePath(interview.getAudioFilePath())
                .audioDuration(interview.getAudioDuration())
                .transcriptText(interview.getTranscriptText())
                .overallScore(interview.getOverallScore())
                .dimensionContent(interview.getDimensionContent())
                .dimensionLogic(interview.getDimensionLogic())
                .dimensionExpression(interview.getDimensionExpression())
                .dimensionProfessional(interview.getDimensionProfessional())
                .dimensionCommunication(interview.getDimensionCommunication())
                .improvementSummary(interview.getImprovementSummary())
                .strengths(interview.getStrengths())
                .weaknesses(interview.getWeaknesses())
                .questions(questionResponses)
                .createdAt(interview.getCreatedAt())
                .build();
    }
}
