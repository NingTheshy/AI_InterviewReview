package com.interview.common.utils;

import com.interview.common.constant.ErrorCode;
import com.interview.common.exception.BusinessException;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Set;
import java.util.UUID;

/**
 * 文件工具类
 * <p>
 * 提供音频文件校验、文件名生成、文件保存等功能。
 * 支持 MP3 和 WAV 格式，最大文件大小 200MB。
 * </p>
 */
public class FileUtil {

    private static final Set<String> ALLOWED_AUDIO_TYPES = Set.of(
            "audio/mpeg",       // MP3
            "audio/wav",        // WAV
            "audio/x-wav",      // WAV (alternative)
            "audio/wave"        // WAV (alternative)
    );

    private static final Set<String> ALLOWED_AUDIO_EXTENSIONS = Set.of(".mp3", ".wav");

    private static final long MAX_FILE_SIZE = 200 * 1024 * 1024; // 200MB

    private static final long MAX_RESUME_SIZE = 20 * 1024 * 1024; // 20MB

    private static final String RESUME_CONTENT_TYPE = "application/pdf";

    private static final String RESUME_EXTENSION = ".pdf";

    private FileUtil() {}

    /**
     * 校验音频文件格式和大小
     *
     * @param file 上传的文件
     * @throws BusinessException 文件为空、格式不支持或大小超限时抛出
     */
    public static void validateAudioFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(), "音频文件不能为空");
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BusinessException(ErrorCode.AUDIO_FILE_TOO_LARGE.getCode(),
                    ErrorCode.AUDIO_FILE_TOO_LARGE.getMessage());
        }

        if (!ALLOWED_AUDIO_TYPES.contains(file.getContentType())) {
            throw new BusinessException(ErrorCode.AUDIO_FORMAT_NOT_SUPPORTED.getCode(),
                    "不支持的音频格式: " + file.getContentType());
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(), "文件名不能为空");
        }

        int dotIndex = originalFilename.lastIndexOf(".");
        String extension = (dotIndex > 0) ? originalFilename.substring(dotIndex).toLowerCase() : "";
        if (!ALLOWED_AUDIO_EXTENSIONS.contains(extension)) {
            throw new BusinessException(ErrorCode.AUDIO_FORMAT_NOT_SUPPORTED.getCode(),
                    ErrorCode.AUDIO_FORMAT_NOT_SUPPORTED.getMessage());
        }
    }

    /**
     * 校验简历文件格式和大小（仅支持 PDF，最大 20MB）
     *
     * @param file 上传的文件
     * @throws BusinessException 文件为空、格式不是 PDF 或大小超限时抛出
     */
    public static void validateResumeFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(), "简历文件不能为空");
        }

        if (file.getSize() > MAX_RESUME_SIZE) {
            throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(), "简历文件大小不能超过 20MB");
        }

        String contentType = file.getContentType();
        if (!RESUME_CONTENT_TYPE.equals(contentType)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(), "简历仅支持 PDF 格式");
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(), "文件名不能为空");
        }

        int dotIndex = originalFilename.lastIndexOf(".");
        String extension = (dotIndex > 0) ? originalFilename.substring(dotIndex).toLowerCase() : "";
        if (!RESUME_EXTENSION.equals(extension)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(), "简历仅支持 PDF 格式");
        }
    }

    /**
     * 生成唯一文件名
     * <p>
     * 使用 UUID 替换原文件名，保留原扩展名。
     * </p>
     *
     * @param originalFilename 原始文件名
     * @return 生成的唯一文件名
     */
    public static String generateFileName(String originalFilename) {
        int dotIndex = originalFilename.lastIndexOf(".");
        String extension = (dotIndex > 0) ? originalFilename.substring(dotIndex) : "";
        return UUID.randomUUID().toString().replace("-", "") + extension;
    }

    /**
     * 保存文件到指定目录
     *
     * @param file      上传的文件
     * @param uploadDir 上传目录路径
     * @param fileName  保存的文件名
     * @return 保存后的文件完整路径
     * @throws IOException 文件保存失败时抛出
     */
    public static Path saveFile(MultipartFile file, String uploadDir, String fileName) throws IOException {
        Path uploadPath = Paths.get(uploadDir).toAbsolutePath().normalize();
        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
        }
        Path filePath = uploadPath.resolve(fileName);
        try (InputStream inputStream = file.getInputStream()) {
            Files.copy(inputStream, filePath, StandardCopyOption.REPLACE_EXISTING);
        }
        return filePath;
    }
}
