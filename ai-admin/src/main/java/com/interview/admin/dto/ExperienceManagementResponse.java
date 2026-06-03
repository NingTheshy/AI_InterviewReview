package com.interview.admin.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 面经管理响应
 * <p>
 * 管理员查看所有公开面经时使用的响应对象。
 * 包含面经基本信息、公开状态和浏览量。
 * </p>
 */
@Data
@Builder
public class ExperienceManagementResponse {

    /** 分享记录 ID */
    private Long id;

    /** 面试 ID */
    private Long interviewId;

    /** 用户 ID */
    private Long userId;

    /** 用户名（关联查询） */
    private String username;

    /** 分享 Token */
    private String shareToken;

    /** 是否公开：0=私有, 1=公开 */
    private Integer isPublic;

    /** 浏览量 */
    private Integer viewCount;

    /** 创建时间 */
    private LocalDateTime createdAt;
}
