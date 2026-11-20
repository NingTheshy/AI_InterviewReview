package com.interview.interview.dto;

import lombok.Builder;
import lombok.Data;
import java.util.List;

/** 仅面经所有者可读取的发布候选项。 */
@Data
@Builder
public class ExperienceEditorResponse {
    private Long id;
    private Long interviewId;
    private String title;
    private String positionTitle;
    private Boolean showReferenceAnswers;
    private String publishStatus;
    private String moderationStatus;
    private List<Long> selectedQuestionIds;
    private List<QuestionItem> questions;

    @Data
    @Builder
    public static class QuestionItem {
        private Long id;
        private Integer questionIndex;
        private String questionText;
        private String referenceAnswer;
        private String questionType;
        private Boolean sensitive;
    }
}
