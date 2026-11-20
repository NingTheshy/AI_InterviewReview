package com.interview.interview.dto;

import com.interview.interview.entity.InterviewExperience;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class ExperiencePublicationResponse {
    private Long id;
    private Long interviewId;
    private String title;
    private String positionTitle;
    private Boolean showReferenceAnswers;
    private List<Long> selectedQuestionIds;
    private String publishStatus;
    private String moderationStatus;
    private LocalDateTime publishedAt;

    public static ExperiencePublicationResponse from(InterviewExperience value) {
        return ExperiencePublicationResponse.builder().id(value.getId()).interviewId(value.getInterviewId())
                .title(value.getTitle()).positionTitle(value.getPositionTitle())
                .showReferenceAnswers(Integer.valueOf(1).equals(value.getShowAnswers()))
                .publishStatus(value.getPublishStatus())
                .moderationStatus(value.getModerationStatus()).publishedAt(value.getPublishedAt()).build();
    }
}
