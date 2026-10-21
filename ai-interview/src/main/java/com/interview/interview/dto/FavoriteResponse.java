package com.interview.interview.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 收藏响应 DTO
 */
@Data
@Builder
public class FavoriteResponse {

    /** 收藏记录 ID */
    private Long id;

    /** 面试 ID */
    private Long interviewId;

    /** 问题 ID */
    private Long questionId;
    private String questionText;
    private String referenceAnswer;
    private Integer score;
    private String interviewTitle;
    private Boolean archived;

    /** 收藏备注 */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createdAt;
}
