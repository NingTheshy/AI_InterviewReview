package com.interview.interview.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/** 发布时保存的题目和答案，不随原面试重评或清理而变化。 */
@Data
@TableName("experience_question_snapshot")
public class ExperienceQuestionSnapshot {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long experienceId;
    private Long sourceQuestionId;
    private Integer questionIndex;
    private String questionText;
    private String referenceAnswer;
    private String questionType;
    private Integer selected;
}
