package com.interview.interview.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.interview.common.constant.ErrorCode;
import com.interview.common.exception.BusinessException;
import com.interview.common.utils.FileUtil;
import com.interview.common.utils.PdfParser;
import com.interview.interview.entity.UserResume;
import com.interview.interview.mapper.UserResumeMapper;
import com.interview.interview.service.ResumeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

/** 用户简历库实现。上传时同步解析，失败的简历不会成为可选版本。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ResumeServiceImpl implements ResumeService {

    private final UserResumeMapper resumeMapper;

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
            String text = PdfParser.extractText(file.getInputStream());
            if (text == null || text.isBlank()) {
                throw new BusinessException(ErrorCode.RESUME_NOT_READY.getCode(), "简历未解析出有效文本，请更换PDF文件");
            }

            UserResume resume = new UserResume();
            resume.setUserId(userId);
            resume.setOriginalName(file.getOriginalFilename());
            resume.setFilePath(storedName);
            resume.setFileSize(file.getSize());
            resume.setContentType(file.getContentType());
            resume.setContentHash(sha256(file.getBytes()));
            resume.setParsedText(text.trim());
            resume.setParseStatus(UserResume.PARSE_SUCCESS);
            resumeMapper.insert(resume);
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
    public UserResume requireReady(Long resumeId, Long userId) {
        if (resumeId == null) {
            throw new BusinessException(ErrorCode.RESUME_REQUIRED.getCode(), ErrorCode.RESUME_REQUIRED.getMessage());
        }
        UserResume resume = resumeMapper.selectById(resumeId);
        if (resume == null || !userId.equals(resume.getUserId())) {
            throw new BusinessException(ErrorCode.RESUME_NOT_FOUND.getCode(), ErrorCode.RESUME_NOT_FOUND.getMessage());
        }
        if (!Integer.valueOf(UserResume.PARSE_SUCCESS).equals(resume.getParseStatus())
                || resume.getParsedText() == null || resume.getParsedText().isBlank()) {
            throw new BusinessException(ErrorCode.RESUME_NOT_READY.getCode(), ErrorCode.RESUME_NOT_READY.getMessage());
        }
        return resume;
    }

    @Override
    public void delete(Long resumeId, Long userId) {
        UserResume resume = requireReady(resumeId, userId);
        resumeMapper.deleteById(resume.getId());
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
            Files.deleteIfExists(path);
        } catch (IOException e) {
            log.warn("清理失败的简历文件失败: {}", path, e);
        }
    }
}
