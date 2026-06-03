package com.interview.interview.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.interview.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 面试笔记实体类
 * <p>
 * 对应数据库 interview_note 表，存储用户对面试或具体问题的个人笔记。
 * 笔记类型通过 {@link com.interview.common.constant.NoteType} 区分：
 * <ul>
 *   <li>INTERVIEW — 面试整体笔记，每场面试每个用户仅一条（覆盖式更新）</li>
 *   <li>QUESTION — 具体问题笔记，每个面试问题每个用户仅一条</li>
 * </ul>
 * </p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("interview_note")
public class InterviewNote extends BaseEntity {

    /** 面试 ID，关联 interview 表 */
    private Long interviewId;

    /** 用户 ID，关联 user 表 */
    private Long userId;

    /** 笔记内容，支持富文本格式（标题、加粗、列表等） */
    private String noteContent;

    /** 笔记类型：INTERVIEW（面试笔记）或 QUESTION（问题笔记） */
    private String noteType;

    /** 问题 ID，仅问题笔记时有值，关联 interview_question 表 */
    private Long questionId;
}
