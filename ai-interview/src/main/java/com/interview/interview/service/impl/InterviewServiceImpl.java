package com.interview.interview.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.interview.ai.audio.AudioPreprocessor;
import com.interview.ai.factory.AiClientFactory;
import com.interview.common.constant.ErrorCode;
import com.interview.common.constant.ConfigType;
import com.interview.common.constant.InterviewStatus;
import com.interview.common.exception.BusinessException;
import com.interview.common.result.PageResult;
import com.interview.interview.dto.InterviewDetailResponse;
import com.interview.interview.dto.InterviewListResponse;
import com.interview.interview.dto.InterviewStatusResponse;
import com.interview.interview.dto.SpeakerRoleMappingRequest;
import com.interview.interview.entity.Interview;
import com.interview.interview.entity.InterviewQuestion;
import com.interview.interview.entity.UserResume;
import com.interview.interview.mapper.InterviewMapper;
import com.interview.interview.mapper.InterviewQuestionMapper;
import com.interview.interview.service.InterviewAsyncService;
import com.interview.interview.service.InterviewService;
import com.interview.interview.service.ResumeService;
import com.interview.interview.service.ExperienceService;
import com.interview.common.utils.FileUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
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

    private static final Set<String> INTERVIEW_TYPES = Set.of(
            "coding", "behavioral", "system_design", "comprehensive");
    private static final Set<String> SORT_FIELDS = Set.of("createdAt", "overallScore");

    private final InterviewMapper interviewMapper;
    private final InterviewQuestionMapper questionMapper;
    private final InterviewAsyncService interviewAsyncService;
    private final ResumeService resumeService;
    private final ExperienceService experienceService;
    private final AudioPreprocessor audioPreprocessor;
    private final AiClientFactory aiClientFactory;
    private final ObjectMapper objectMapper;

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
    @Transactional
    public Long upload(Long userId, MultipartFile audioFile, Long resumeId,
                       String jdText, String title, String companyName,
                       String positionTitle, String industry, String interviewType) {
        FileUtil.validateAudioFile(audioFile);
        if (jdText == null || jdText.isBlank()) {
            throw badRequest("岗位JD不能为空");
        }
        if (positionTitle == null || positionTitle.isBlank()) {
            throw badRequest("岗位名称不能为空");
        }
        if (interviewType == null || !INTERVIEW_TYPES.contains(interviewType.trim().toLowerCase())) {
            throw badRequest("面试类型只能是coding、behavioral、system_design或comprehensive");
        }
        interviewType = interviewType.trim().toLowerCase();
        positionTitle = positionTitle.trim();
        companyName = trimToNull(companyName);
        industry = trimToNull(industry);

        UserResume resume = resumeService.requireReady(resumeId, userId);

        Interview active = interviewMapper.selectActiveForUpdate(userId);
        if (active != null) {
            throw new BusinessException(ErrorCode.INTERVIEW_PROCESSING.getCode(),
                    "当前已有面试正在处理中，请完成或等待失败后再开始新的面试");
        }
        String asrSnapshot = aiClientFactory.snapshotDefault(ConfigType.ASR);
        String llmSnapshot = aiClientFactory.snapshotDefault(ConfigType.LLM);

        String audioFileName = FileUtil.generateFileName(audioFile.getOriginalFilename());
        Path savedAudio;
        try {
            savedAudio = FileUtil.saveFile(audioFile, uploadDir, audioFileName);
        } catch (IOException e) {
            log.error("保存音频文件失败", e);
            throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(), "文件保存失败");
        }

        int audioDuration;
        try {
            double duration = audioPreprocessor.probeDuration(savedAudio);
            if (duration > 7200D) {
                throw new BusinessException(ErrorCode.AUDIO_DURATION_EXCEEDED.getCode(),
                        ErrorCode.AUDIO_DURATION_EXCEEDED.getMessage());
            }
            audioDuration = (int) Math.ceil(duration);
        } catch (RuntimeException e) {
            try {
                Files.deleteIfExists(savedAudio);
            } catch (IOException cleanupError) {
                log.warn("清理未创建任务的音频失败: {}", savedAudio, cleanupError);
            }
            throw e;
        }

        Interview interview = new Interview();
        interview.setUserId(userId);
        interview.setTitle(defaultTitle(title, companyName, positionTitle));
        interview.setCompanyName(companyName);
        interview.setPositionTitle(positionTitle);
        interview.setIndustry(industry);
        interview.setInterviewType(interviewType);
        interview.setResumeId(resume.getId());
        interview.setJdText(jdText);
        interview.setAudioFilePath(audioFileName);
        interview.setAudioFileSize(audioFile.getSize());
        interview.setAudioDuration(audioDuration);
        // 保留旧字段作为历史兼容快照；简历主数据由 user_resume 管理。
        interview.setResumeFilePath(resume.getFilePath());
        interview.setResumeText(resume.getParsedText());
        interview.setStatus(InterviewStatus.PROCESSING.getCode());
        interview.setProcessingStep(0);
        interview.setProcessingToken(UUID.randomUUID().toString());
        interview.setHeartbeatAt(LocalDateTime.now());
        interview.setLeaseExpiresAt(LocalDateTime.now().plusMinutes(5));
        interview.setAsrConfigSnapshot(asrSnapshot);
        interview.setLlmConfigSnapshot(llmSnapshot);

        try {
            interviewMapper.insert(interview);
        } catch (RuntimeException e) {
            try {
                Files.deleteIfExists(savedAudio);
            } catch (IOException cleanupError) {
                log.warn("清理数据库写入失败后的音频失败: {}", savedAudio, cleanupError);
            }
            throw e;
        }

        submitAfterCommit(interview.getId(), interview.getProcessingToken());

        log.info("面试记录创建成功: id={}, title={}", interview.getId(), title);
        return interview.getId();
    }

    /** 确保异步线程只能在面试记录事务提交后读取任务。 */
    private void submitAfterCommit(Long interviewId, String token) {
        Runnable submit = () -> {
            try {
                interviewAsyncService.processInterview(interviewId, token);
            } catch (RuntimeException e) {
                log.error("提交面试异步任务失败: interviewId={}", interviewId, e);
                Interview failed = interviewMapper.selectById(interviewId);
                if (failed != null && token.equals(failed.getProcessingToken())) {
                    failed.setStatus(InterviewStatus.FAILED.getCode());
                    failed.setLastErrorCode("TASK_REJECTED");
                    failed.setErrorMessage("系统任务队列繁忙，请稍后重试");
                    interviewMapper.updateById(failed);
                }
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    submit.run();
                }
            });
        } else {
            submit.run();
        }
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
                                                   String keyword, Integer status, String sortBy) {
        if (sortBy == null || sortBy.isBlank()) sortBy = "createdAt";
        if (page < 1 || size < 1 || size > 100) {
            throw badRequest("分页参数不合法，page至少为1且size范围为1-100");
        }
        if (!SORT_FIELDS.contains(sortBy)) {
            throw badRequest("不支持的排序字段");
        }
        if (status != null && !Set.of(0, 1, 2, 3).contains(status)) {
            throw badRequest("面试状态不合法");
        }
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
        if (status != null) wrapper.eq(Interview::getStatus, status);
        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(w -> w.like(Interview::getTitle, keyword.trim())
                    .or().like(Interview::getCompanyName, keyword.trim())
                    .or().like(Interview::getPositionTitle, keyword.trim()));
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
        List<InterviewQuestion> activeQuestions = questions.stream()
                .filter(q -> q.getIsActive() == null || q.getIsActive() == 1).toList();
        List<InterviewQuestion> archivedQuestions = questions.stream()
                .filter(q -> Integer.valueOf(0).equals(q.getIsActive())).toList();
        return toInterviewDetailResponse(interview, activeQuestions, archivedQuestions);
    }

    /**
     * {@inheritDoc}
     * <p>
     * 实现逻辑：校验面试存在性及用户所有权后执行软删除。
     * </p>
     */
    @Override
    @Transactional
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

        if (interviewMapper.moveToRecycleBin(id, userId) == 0) {
            throw new BusinessException(ErrorCode.INTERVIEW_NOT_FOUND.getCode(),
                    ErrorCode.INTERVIEW_NOT_FOUND.getMessage());
        }
        experienceService.withdrawForInterview(id);
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
                .errorCode(interview.getLastErrorCode())
                .errorMessage(interview.getErrorMessage())
                .reanalysisStatus(interview.getReanalysisStatus())
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
        if (status == 3) return 100; // CANCELLED

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
            case 1 -> "FunASR转写与说话人识别";
            case 2 -> "标准答案生成与逐项比对";
            case 3 -> "服务端计分与结果保存";
            case 4 -> "整体评分与总结";
            default -> "未知步骤";
        };
    }

    /**
     * {@inheritDoc}
     * <p>
     * 实现逻辑：
     * 1. 校验面试存在性及用户所有权
     * 2. 仅拒绝处理中的记录（防止重复处理）
     * 3. 重置状态为 PROCESSING，处理步骤归零
     * 4. 重新触发异步处理（从语音转文字开始全流程重跑）
     * </p>
     * <p>
     * 支持场景：
     * - 失败记录重试：处理失败后修复问题重新处理
     * - 手动重新评分：已完成的面试用更新后的评分策略重新评估
     * </p>
     */
    @Override
    @Transactional
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
        Interview active = interviewMapper.selectActiveForUpdate(userId);
        if (active != null && !active.getId().equals(id)) {
            throw new BusinessException(ErrorCode.INTERVIEW_PROCESSING.getCode(),
                    "当前已有其他面试正在处理中");
        }
        if (!Set.of(InterviewStatus.FAILED.getCode(), InterviewStatus.CANCELLED.getCode())
                .contains(interview.getStatus())) {
            throw badRequest("只有失败或已取消的面试可以重试");
        }

        // 记录原状态用于日志
        String previousStatus = InterviewStatus.fromCode(interview.getStatus()).getDescription();

        String token = UUID.randomUUID().toString();
        if (interviewMapper.claimTask(id, token, LocalDateTime.now().plusMinutes(5)) == 0) {
            throw new BusinessException(ErrorCode.INTERVIEW_PROCESSING.getCode(),
                    ErrorCode.INTERVIEW_PROCESSING.getMessage());
        }
        // 转写已完整落库时从问答阶段继续，否则从头开始。
        interview.setStatus(InterviewStatus.PROCESSING.getCode());
        interview.setProcessingToken(token);
        interview.setLeaseExpiresAt(LocalDateTime.now().plusMinutes(5));
        interview.setHeartbeatAt(LocalDateTime.now());
        interview.setProcessingStep(interview.getTranscriptSegments() == null ? 0 : 1);
        interview.setLastErrorCode(null);
        interview.setAsrConfigSnapshot(aiClientFactory.snapshotDefault(ConfigType.ASR));
        interview.setLlmConfigSnapshot(aiClientFactory.snapshotDefault(ConfigType.LLM));
        interview.setErrorMessage(null);
        interviewMapper.updateById(interview);

        submitAfterCommit(id, token);

        log.info("重新处理面试: id={}, 之前状态={}", id, previousStatus);
    }

    @Override
    @Transactional
    public void cancel(Long id, Long userId) {
        Interview interview = ownedInterview(id, userId);
        if (!Integer.valueOf(InterviewStatus.PROCESSING.getCode()).equals(interview.getStatus())) {
            throw badRequest("只有处理中的面试可以取消");
        }
        interview.setStatus(InterviewStatus.CANCELLED.getCode());
        interview.setProcessingToken(null);
        interview.setLeaseExpiresAt(null);
        interview.setHeartbeatAt(LocalDateTime.now());
        interview.setLastErrorCode("CANCELLED_BY_USER");
        interview.setErrorMessage("用户已取消处理");
        interviewMapper.updateById(interview);
    }

    @Override
    @Transactional
    public void reanalyze(Long id, Long userId) {
        Interview interview = ownedInterview(id, userId);
        if (!Integer.valueOf(InterviewStatus.COMPLETED.getCode()).equals(interview.getStatus())) {
            throw badRequest("只有已完成面试可以重新分析");
        }
        experienceService.withdrawForInterview(id);
        startReanalysis(interview);
    }

    @Override
    @Transactional
    public void updateTitle(Long id, Long userId, String title) {
        Interview interview = ownedInterview(id, userId);
        if (title == null || title.isBlank() || title.trim().length() > 200) {
            throw badRequest("标题不能为空且不能超过200个字符");
        }
        interview.setTitle(title.trim());
        interviewMapper.updateById(interview);
    }

    @Override
    public List<InterviewListResponse> recycleBin(Long userId) {
        return interviewMapper.selectRecycleBin(userId).stream()
                .map(this::toInterviewListResponse)
                .toList();
    }

    @Override
    @Transactional
    public void restore(Long id, Long userId) {
        Interview deleted = interviewMapper.selectDeletedById(id);
        if (deleted == null || !userId.equals(deleted.getUserId())) {
            throw new BusinessException(ErrorCode.INTERVIEW_NOT_FOUND.getCode(),
                    ErrorCode.INTERVIEW_NOT_FOUND.getMessage());
        }
        if (deleted.getPurgedAt() != null || deleted.getPurgeAfter() == null
                || !deleted.getPurgeAfter().isAfter(LocalDateTime.now())) {
            throw new BusinessException(ErrorCode.INTERVIEW_RESTORE_EXPIRED.getCode(),
                    ErrorCode.INTERVIEW_RESTORE_EXPIRED.getMessage());
        }
        if (interviewMapper.restore(id, userId) == 0) {
            throw new BusinessException(ErrorCode.INTERVIEW_RESTORE_EXPIRED.getCode(),
                    ErrorCode.INTERVIEW_RESTORE_EXPIRED.getMessage());
        }
        log.info("恢复面试记录: id={}, userId={}", id, userId);
    }

    @Override
    @Transactional
    public void updateSpeakerRoles(Long id, Long userId, SpeakerRoleMappingRequest request) {
        Interview interview = interviewMapper.selectById(id);
        if (interview == null) {
            throw new BusinessException(ErrorCode.INTERVIEW_NOT_FOUND.getCode(), ErrorCode.INTERVIEW_NOT_FOUND.getMessage());
        }
        if (!userId.equals(interview.getUserId())) {
            throw new BusinessException(ErrorCode.INTERVIEW_ACCESS_DENIED.getCode(), ErrorCode.INTERVIEW_ACCESS_DENIED.getMessage());
        }
        Interview active = interviewMapper.selectActiveForUpdate(userId);
        if (active != null) {
            throw new BusinessException(ErrorCode.INTERVIEW_PROCESSING.getCode(), ErrorCode.INTERVIEW_PROCESSING.getMessage());
        }
        try {
            JsonNode parsed = objectMapper.readTree(interview.getTranscriptSegments());
            if (!(parsed instanceof ArrayNode segments) || segments.isEmpty()) {
                throw new BusinessException(ErrorCode.INVALID_SPEAKER_MAPPING.getCode(), "当前面试没有可校正的说话人转写");
            }
            Map<String, String> roles = new HashMap<>();
            for (SpeakerRoleMappingRequest.Mapping mapping : request.getMappings()) {
                String role = mapping.getRole().trim().toUpperCase();
                if (!Set.of("INTERVIEWER", "CANDIDATE").contains(role)
                        || roles.put(mapping.getSpeakerId(), role) != null) {
                    throw new BusinessException(ErrorCode.INVALID_SPEAKER_MAPPING.getCode(),
                            "speakerId不能重复，role只能是INTERVIEWER或CANDIDATE");
                }
            }
            long candidates = roles.values().stream().filter("CANDIDATE"::equals).count();
            long interviewers = roles.values().stream().filter("INTERVIEWER"::equals).count();
            if (candidates != 1 || interviewers < 1) {
                throw new BusinessException(ErrorCode.INVALID_SPEAKER_MAPPING.getCode(),
                        "必须指定一名候选人和至少一名面试官");
            }
            Set<String> actualSpeakers = new HashSet<>();
            for (JsonNode segment : segments) {
                if (segment.path("speakerId").isTextual()) actualSpeakers.add(segment.path("speakerId").asText());
            }
            if (!actualSpeakers.containsAll(roles.keySet())) {
                throw new BusinessException(ErrorCode.INVALID_SPEAKER_MAPPING.getCode(), "映射包含转写中不存在的speakerId");
            }

            StringBuilder readable = new StringBuilder();
            for (JsonNode segment : segments) {
                if (!(segment instanceof ObjectNode object)) continue;
                String speakerId = segment.path("speakerId").asText();
                String role = roles.getOrDefault(speakerId, "UNKNOWN");
                object.put("speakerRole", role);
                if (readable.length() > 0) readable.append('\n');
                String label = "INTERVIEWER".equals(role) ? "面试官"
                        : "CANDIDATE".equals(role) ? "候选人" : "说话人 " + speakerId;
                readable.append('[').append(label).append("] ").append(segment.path("text").asText());
            }
            String token = UUID.randomUUID().toString();
            interview.setTranscriptSegments(objectMapper.writeValueAsString(segments));
            interview.setTranscriptText(readable.toString());
            interview.setStatus(InterviewStatus.PROCESSING.getCode());
            interview.setProcessingStep(1);
            interview.setProcessingToken(token);
            interview.setHeartbeatAt(LocalDateTime.now());
            interview.setLeaseExpiresAt(LocalDateTime.now().plusMinutes(5));
            interview.setReanalysisStatus("PROCESSING");
            interview.setReanalysisError(null);
            interview.setLlmConfigSnapshot(aiClientFactory.snapshotDefault(ConfigType.LLM));
            experienceService.withdrawForInterview(id);
            interviewMapper.updateById(interview);
            submitReanalysisAfterCommit(id, token);
        } catch (BusinessException e) {
            throw e;
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.INVALID_SPEAKER_MAPPING.getCode(), "转写数据损坏，无法校正说话人");
        }
    }

    private void submitReanalysisAfterCommit(Long interviewId, String token) {
        Runnable submit = () -> {
            try {
                interviewAsyncService.reanalyzeInterview(interviewId, token);
            } catch (RuntimeException e) {
                Interview current = interviewMapper.selectById(interviewId);
                if (current != null && token.equals(current.getProcessingToken())) {
                    current.setStatus(InterviewStatus.COMPLETED.getCode());
                    current.setReanalysisStatus("FAILED");
                    current.setReanalysisError("系统任务队列繁忙，请稍后重试");
                    current.setProcessingToken(null);
                    current.setLeaseExpiresAt(null);
                    interviewMapper.updateById(current);
                }
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { submit.run(); }
            });
        } else {
            submit.run();
        }
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
                                                              List<InterviewQuestion> questions,
                                                              List<InterviewQuestion> archivedQuestions) {
        List<InterviewDetailResponse.QuestionResponse> questionResponses = questions.stream()
                .map(this::toQuestionResponse)
                .collect(Collectors.toList());
        List<InterviewDetailResponse.QuestionResponse> archivedResponses = archivedQuestions.stream()
                .map(this::toQuestionResponse).collect(Collectors.toList());

        return InterviewDetailResponse.builder()
                .id(interview.getId())
                .title(interview.getTitle())
                .companyName(interview.getCompanyName())
                .positionTitle(interview.getPositionTitle())
                .industry(interview.getIndustry())
                .interviewType(interview.getInterviewType())
                .status(interview.getStatus())
                .processingStep(interview.getProcessingStep())
                .lastErrorCode(interview.getLastErrorCode())
                .errorMessage(interview.getErrorMessage())
                .reanalysisStatus(interview.getReanalysisStatus())
                .reanalysisError(interview.getReanalysisError())
                .resumeId(interview.getResumeId())
                .audioFilePath(interview.getAudioFilePath())
                .audioDuration(interview.getAudioDuration())
                .transcriptText(interview.getTranscriptText())
                .transcriptSegments(parseJson(interview.getTranscriptSegments(), "句子级转写"))
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
                .archivedQuestions(archivedResponses)
                .createdAt(interview.getCreatedAt())
                .build();
    }

    private InterviewDetailResponse.QuestionResponse toQuestionResponse(InterviewQuestion q) {
        return InterviewDetailResponse.QuestionResponse.builder()
                        .id(q.getId())
                        .questionIndex(q.getQuestionIndex())
                        .questionText(q.getQuestionText())
                        .questionType(q.getQuestionType())
                        .recognitionConfidence(q.getRecognitionConfidence())
                        .includedInScore(Integer.valueOf(1).equals(q.getIncludedInScore()))
                        .questionSpeaker(q.getQuestionSpeaker())
                        .answerText(q.getAnswerText())
                        .answerSpeaker(q.getAnswerSpeaker())
                        .startTime(q.getStartTime())
                        .endTime(q.getEndTime())
                        .score(q.getScore())
                        .dimensionContent(q.getDimensionContent())
                        .dimensionLogic(q.getDimensionLogic())
                        .dimensionExpression(q.getDimensionExpression())
                        .dimensionProfessional(q.getDimensionProfessional())
                        .improvementTip(q.getImprovementTip())
                        .referenceAnswer(q.getReferenceAnswer())
                        .answerDifference(q.getAnswerDifference())
                        .rubricDetail(parseRubricDetail(q.getRubricDetail()))
                .build();
    }

    private JsonNode parseRubricDetail(String value) {
        return parseJson(value, "评分量表");
    }

    private Interview ownedInterview(Long id, Long userId) {
        Interview interview = interviewMapper.selectById(id);
        if (interview == null) {
            throw new BusinessException(ErrorCode.INTERVIEW_NOT_FOUND.getCode(), ErrorCode.INTERVIEW_NOT_FOUND.getMessage());
        }
        if (!userId.equals(interview.getUserId())) {
            throw new BusinessException(ErrorCode.INTERVIEW_ACCESS_DENIED.getCode(), ErrorCode.INTERVIEW_ACCESS_DENIED.getMessage());
        }
        return interview;
    }

    private void startReanalysis(Interview interview) {
        Interview active = interviewMapper.selectActiveForUpdate(interview.getUserId());
        if (active != null) throw new BusinessException(ErrorCode.INTERVIEW_PROCESSING.getCode(), "当前已有其他面试正在处理中");
        String token = UUID.randomUUID().toString();
        interview.setStatus(InterviewStatus.PROCESSING.getCode());
        interview.setProcessingStep(1);
        interview.setProcessingToken(token);
        interview.setHeartbeatAt(LocalDateTime.now());
        interview.setLeaseExpiresAt(LocalDateTime.now().plusMinutes(5));
        interview.setReanalysisStatus("PROCESSING");
        interview.setReanalysisError(null);
        interview.setLlmConfigSnapshot(aiClientFactory.snapshotDefault(ConfigType.LLM));
        interviewMapper.updateById(interview);
        submitReanalysisAfterCommit(interview.getId(), token);
    }

    private BusinessException badRequest(String message) {
        return new BusinessException(ErrorCode.BAD_REQUEST.getCode(), message);
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim();
    }

    private String defaultTitle(String title, String company, String position) {
        if (title != null && !title.isBlank()) return title.trim();
        String date = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
        return company == null ? position + "-" + date : company + "-" + position + "-" + date;
    }

    private JsonNode parseJson(String value, String description) {
        if (value == null || value.isBlank()) return null;
        try {
            return objectMapper.readTree(value);
        } catch (IOException e) {
            log.warn("{} JSON 解析失败，忽略异常明细", description, e);
            return null;
        }
    }
}
