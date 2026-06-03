package com.interview.interview.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 面试上传请求
 */
@Data
public class InterviewUploadRequest {

    @NotBlank(message = "JD 文本不能为空")
    private String jdText;

    private String title;

    private String companyName;

    private String positionTitle;

    private String industry;

    private String interviewType;
}
