package com.interview.interview.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 面经广场列表响应 DTO
 * <p>
 * 广场卡片仅显示作者、标题、岗位、题数和互动数量。
 * </p>
 */
@Data
@Builder
public class ExperienceListResponse {

    /** 分享 Token */
    private Long id;
    private String authorNickname;
    private String authorAvatar;

    /** 面试标题 */
    private String title;

    /** 职位名称 */
    private String positionTitle;

    /** 问题数量 */
    private Integer questionCount;

    /** 浏览量 */
    private Integer viewCount;
    private Integer likeCount;
    private Integer favoriteCount;

    /** 发布时间 */
    private LocalDateTime publishedAt;
}
