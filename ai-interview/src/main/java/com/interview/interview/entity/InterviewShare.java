package com.interview.interview.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.interview.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 面试分享实体类
 * <p>
 * 对应数据库 interview_share 表，存储面试记录的分享链接信息。
 * 每个分享记录包含唯一 Token、过期时间、公开状态和浏览量。
 * </p>
 * <p>
 * 使用逻辑删除，删除后在面经广场不可见，但原有分享链接仍可访问（标记为已下架）。
 * </p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("interview_share")
public class InterviewShare extends BaseEntity {

    /** 面试 ID，关联 interview 表 */
    private Long interviewId;

    /** 用户 ID，关联 user 表 */
    private Long userId;

    /** 分享 Token，UUID 去连字符，用于唯一标识分享链接 */
    private String shareToken;

    /** 过期时间，null 表示永不过期 */
    private LocalDateTime expireAt;

    /** 是否公开到面经广场：0=私有，1=公开 */
    private Integer isPublic;

    /** 浏览量 */
    private Integer viewCount;
}
