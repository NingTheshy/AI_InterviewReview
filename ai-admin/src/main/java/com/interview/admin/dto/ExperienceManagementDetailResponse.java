package com.interview.admin.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/** 管理员预览面经实际公开内容；不包含原面试的私人数据。 */
@Data
@Builder
public class ExperienceManagementDetailResponse {
    private Long id;
    private String title;
    private String positionTitle;
    private String companyName;
    private String username;
    private String publishStatus;
    private String moderationStatus;
    private Boolean showReferenceAnswers;
    private LocalDateTime publishedAt;
    private List<QuestionItem> questions;

    @Data
    @Builder
    public static class QuestionItem {
        private Integer questionIndex;
        private String questionText;
        private String referenceAnswer;
    }
}
