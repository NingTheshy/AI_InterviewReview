package com.interview.interview.dto;

import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.Data;

@Data
public class ExperiencePublicationRequest {
    @Size(max = 200, message = "面经标题不能超过200个字符")
    private String title;
    private List<Long> selectedQuestionIds;
    private Boolean showReferenceAnswers;
    private List<Long> confirmedSensitiveQuestionIds;
    /** 从已删除的指定旧面经快照重新发布时填写。 */
    private Long sourceExperienceId;
}
