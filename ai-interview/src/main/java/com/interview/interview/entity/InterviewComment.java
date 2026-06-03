package com.interview.interview.entity;

import com.baomidou.mybatisplus.annotation.TableName;
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

    /** 面经分享 ID，关联 interview_share 表 */
    private Long shareId;

    /** 评论者用户 ID，关联 user 表 */
    private Long userId;

    /** 评论内容，最长 500 字符 */
    private String content;
}
