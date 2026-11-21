package com.interview.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 管理员只可修改面试的四项基础信息。 */
@Data
public class AdminInterviewEditRequest {
    @NotBlank @Size(max = 200)
    private String title;
    @Size(max = 100)
    private String companyName;
    @NotBlank @Size(max = 100)
    private String positionTitle;
    @Size(max = 50)
    private String interviewRound;
}
