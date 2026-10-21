package com.interview.interview.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 面试上传请求
 */
@Data
public class InterviewUploadRequest {

    @NotNull(message = "简历 ID 不能为空")
    private Long resumeId;

    @NotBlank(message = "JD 文本不能为空")
    private String jdText;

    private String title;

    private String companyName;

    @NotBlank(message = "岗位名称不能为空")
    private String positionTitle;

    private String industry;

    @NotBlank(message = "面试类型不能为空")
    private String interviewType;
}
