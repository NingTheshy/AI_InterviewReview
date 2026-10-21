package com.interview.interview.dto;

import com.interview.interview.entity.InterviewExperience;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class ExperiencePublicationResponse {
    private Long id;
    private Long interviewId;
    private String summaryMarkdown;
    private String publishStatus;
    private String moderationStatus;
    private LocalDateTime publishedAt;

    public static ExperiencePublicationResponse from(InterviewExperience value) {
        return ExperiencePublicationResponse.builder().id(value.getId()).interviewId(value.getInterviewId())
                .summaryMarkdown(value.getSummaryMarkdown()).publishStatus(value.getPublishStatus())
                .moderationStatus(value.getModerationStatus()).publishedAt(value.getPublishedAt()).build();
    }
}
