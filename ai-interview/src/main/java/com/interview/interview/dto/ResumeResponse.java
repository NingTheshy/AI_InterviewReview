package com.interview.interview.dto;

import com.interview.interview.entity.UserResume;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/** 用户简历版本响应，不返回解析后的完整正文。 */
@Data
@Builder
public class ResumeResponse {
    private Long id;
    private String originalName;
    private Long fileSize;
    private Integer parseStatus;
    private String parseError;
    private Integer retryCount;
    private LocalDateTime createdAt;

    public static ResumeResponse from(UserResume resume) {
        return ResumeResponse.builder()
                .id(resume.getId())
                .originalName(resume.getOriginalName())
                .fileSize(resume.getFileSize())
                .parseStatus(resume.getParseStatus())
                .parseError(resume.getParseError())
                .retryCount(resume.getRetryCount())
                .createdAt(resume.getCreatedAt())
                .build();
    }
}
