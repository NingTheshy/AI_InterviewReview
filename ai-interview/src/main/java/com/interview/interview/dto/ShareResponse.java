package com.interview.interview.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 分享响应 DTO
 */
@Data
@Builder
public class ShareResponse {

    /** 分享记录 ID */
    private Long id;

    /** 面试 ID */
    private Long interviewId;

    /** 分享 Token */
    private String token;

    /** 过期时间，null 表示永不过期 */
    private LocalDateTime expireAt;

    /** 是否公开到面经广场：0=私有，1=公开 */
    private Integer isPublic;

    /** 浏览量 */
    private Integer viewCount;

    /** 创建时间 */
    private LocalDateTime createdAt;
}
