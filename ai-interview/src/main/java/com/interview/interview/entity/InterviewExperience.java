package com.interview.interview.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.interview.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/** 一场已完成面试对应的公开面经发布记录。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("interview_experience")
public class InterviewExperience extends BaseEntity {
    public static final String PUBLISHED = "PUBLISHED";
    public static final String WITHDRAWN = "WITHDRAWN";
    public static final String NORMAL = "NORMAL";
    public static final String BLOCKED = "BLOCKED";

    private Long interviewId;
    private Long userId;
    private String summaryMarkdown;
    private String publishStatus;
    private String moderationStatus;
    private LocalDateTime publishedAt;
    private Integer viewCount;

    public boolean isVisible() {
        return PUBLISHED.equals(publishStatus) && NORMAL.equals(moderationStatus);
    }
}
