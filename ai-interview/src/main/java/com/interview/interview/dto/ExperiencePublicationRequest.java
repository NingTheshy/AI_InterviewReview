package com.interview.interview.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ExperiencePublicationRequest {
    @Size(max = 10000, message = "经验总结不能超过10000个字符")
    private String summaryMarkdown;
}
