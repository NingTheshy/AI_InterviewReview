package com.interview.interview.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableField;
import com.interview.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 面经评论实体类
 * <p>
 * 对应数据库 interview_comment 表，存储用户对公开面经的评论。
 * 支持评论作者和面经发布者删除评论。
 * </p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("interview_comment")
public class InterviewComment extends BaseEntity {

    /** 公开面经 ID，关联 interview_experience 表 */
    private Long experienceId;

    /** 直接回复的评论 ID；顶层评论为空。 */
    private Long parentId;

    /** 列表查询附带的直接回复数量，不写入数据库。 */
    @TableField(exist = false)
    private Long replyCount;

    /** 被回复者用户 ID，在父评论清理后仍可显示回复对象。 */
    private Long replyToUserId;

    /** 评论删除时间。 */
    private java.time.LocalDateTime deletedAt;

    /** 删除后七天的清理时间。 */
    private java.time.LocalDateTime purgeAfter;

    @TableField(exist = false)
    private String authorUsername;

    @TableField(exist = false)
    private String authorNickname;

    @TableField(exist = false)
    private String experienceTitle;

    @TableField(exist = false)
    private String parentContent;

    /** 评论者用户 ID，关联 user 表 */
    private Long userId;

    /** 评论内容，最长 500 字符 */
    private String content;
}
