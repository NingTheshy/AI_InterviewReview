package com.interview.interview.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 面经详情响应 DTO
 * <p>
 * 仅返回作者选定的题目快照及按整篇开关允许公开的标准答案。
 * 不返回原面试评分、候选人回答或其他私人复盘数据。
 * </p>
 */
@Data
@Builder
public class ExperienceDetailResponse {

    /** 公开面经数字 ID */
    private Long id;
    private String authorNickname;
    private String authorAvatar;

    /** 面经标题 */
    private String title;

    /** 职位名称 */
    private String positionTitle;

    /** 问题列表 */
    private List<QuestionItem> questions;

    /** 浏览量 */
    private Integer viewCount;
    private Integer likeCount;
    private Integer favoriteCount;
    private Integer commentCount;
    private Boolean liked;
    private Boolean favorited;

    /** 发布时间 */
    private LocalDateTime publishedAt;

    /**
     * 问题条目
     */
    @Data
    @Builder
    public static class QuestionItem {
        private Long id;
        private Integer questionIndex;
        private String questionText;
        private String referenceAnswer;
    }
}
