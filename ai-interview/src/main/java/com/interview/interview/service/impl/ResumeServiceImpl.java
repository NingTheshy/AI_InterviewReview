package com.interview.interview.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.interview.common.constant.ErrorCode;
import com.interview.common.exception.BusinessException;
import com.interview.common.utils.FileUtil;
import com.interview.interview.entity.UserResume;
import com.interview.interview.mapper.UserResumeMapper;
import com.interview.interview.service.ResumeService;
import com.interview.interview.service.ResumeAsyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** 用户简历库实现。上传请求只持久化文件，正文由后台任务解析。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ResumeServiceImpl implements ResumeService {

    private final UserResumeMapper resumeMapper;
    private final ResumeAsyncService resumeAsyncService;

    @Value("${interview.file.upload-dir:./uploads}")
    private String uploadDir;

    @Override
    @Transactional
    public UserResume upload(Long userId, MultipartFile file) {
        FileUtil.validateResumeFile(file);
        String storedName = FileUtil.generateFileName(file.getOriginalFilename());
        Path saved = null;
        try {
            saved = FileUtil.saveFile(file, uploadDir, storedName);
            String hash = sha256(file.getBytes());
            UserResume reusable = resumeMapper.findReusable(userId, hash);
            if (reusable != null) {
                java.nio.file.Files.deleteIfExists(saved);
                return reusable;
            }

            UserResume resume = new UserResume();
            resume.setUserId(userId);
            resume.setOriginalName(file.getOriginalFilename());
            resume.setFilePath(storedName);
            resume.setFileSize(file.getSize());
            resume.setContentType(file.getContentType());
            resume.setContentHash(hash);
            resume.setParsedText("");
            resume.setParseStatus(UserResume.PARSE_PROCESSING);
            resume.setRetryCount(0);
            String token = UUID.randomUUID().toString();
            resume.setProcessingToken(token);
            resume.setHeartbeatAt(LocalDateTime.now());
            resume.setLeaseExpiresAt(LocalDateTime.now().plusMinutes(5));
            resumeMapper.insert(resume);
            submitAfterCommit(resume, token);
            return resume;
        } catch (BusinessException e) {
            deleteQuietly(saved);
            throw e;
        } catch (IOException e) {
            deleteQuietly(saved);
            log.warn("简历保存或解析失败: userId={}, file={}", userId, file.getOriginalFilename(), e);
            throw new BusinessException(ErrorCode.RESUME_NOT_READY.getCode(), "简历解析失败，请更换PDF文件");
        }
    }

    @Override
    public List<UserResume> list(Long userId) {
        return resumeMapper.selectList(new LambdaQueryWrapper<UserResume>()
                .eq(UserResume::getUserId, userId)
                .orderByDesc(UserResume::getCreatedAt));
    }

    @Override
    public UserResume get(Long resumeId, Long userId) {
        UserResume resume = resumeMapper.selectById(resumeId);
        if (resume == null || !userId.equals(resume.getUserId())) {
            throw new BusinessException(ErrorCode.RESUME_NOT_FOUND.getCode(), ErrorCode.RESUME_NOT_FOUND.getMessage());
        }
        return resume;
    }

    @Override
    public UserResume requireReady(Long resumeId, Long userId) {
        if (resumeId == null) {
            throw new BusinessException(ErrorCode.RESUME_REQUIRED.getCode(), ErrorCode.RESUME_REQUIRED.getMessage());
        }
        UserResume resume = get(resumeId, userId);
        if (!Integer.valueOf(UserResume.PARSE_SUCCESS).equals(resume.getParseStatus())
                || resume.getParsedText() == null || resume.getParsedText().isBlank()) {
            throw new BusinessException(ErrorCode.RESUME_NOT_READY.getCode(), ErrorCode.RESUME_NOT_READY.getMessage());
        }
        return resume;
    }

    @Override
    @Transactional
    public void retry(Long resumeId, Long userId) {
        UserResume resume = get(resumeId, userId);
        if (Integer.valueOf(UserResume.PARSE_PROCESSING).equals(resume.getParseStatus())
                && resume.getLeaseExpiresAt() != null && resume.getLeaseExpiresAt().isAfter(LocalDateTime.now())) {
            throw new BusinessException(ErrorCode.RESUME_NOT_READY.getCode(), "简历正在解析中");
        }
        String token = UUID.randomUUID().toString();
        resume.setParseStatus(UserResume.PARSE_PROCESSING);
        resume.setParseError(null);
        resume.setParsedText("");
        resume.setProcessingToken(token);
        resume.setHeartbeatAt(LocalDateTime.now());
        resume.setLeaseExpiresAt(LocalDateTime.now().plusMinutes(5));
        resume.setRetryCount((resume.getRetryCount() == null ? 0 : resume.getRetryCount()) + 1);
        resumeMapper.updateById(resume);
        submitAfterCommit(resume, token);
    }

    @Override
    public void delete(Long resumeId, Long userId) {
        UserResume resume = get(resumeId, userId);
        resumeMapper.deleteById(resume.getId());
    }

    private void submitAfterCommit(UserResume resume, String token) {
        Runnable submit = () -> {
            try {
                resumeAsyncService.parse(resume.getId(), token);
            } catch (RuntimeException e) {
                resumeMapper.failParse(resume.getId(), token, "系统任务队列繁忙，请稍后重试");
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

    private String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("JVM不支持SHA-256", e);
        }
    }

    private void deleteQuietly(Path path) {
        if (path == null) return;
        try {
            java.nio.file.Files.deleteIfExists(path);
        } catch (IOException e) {
            log.warn("清理失败的简历文件失败: {}", path, e);
        }
    }
}
