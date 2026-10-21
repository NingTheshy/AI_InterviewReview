package com.interview.interview.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.interview.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 标准答案和量表缓存。个性化题必须携带用户、面试和简历版本作用域。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("question_standard_cache")
public class QuestionStandardCache extends BaseEntity {
    private String cacheKey;
    private String scopeType;
    private String questionText;
    private String contextDigest;
    private Long userId;
    private Long interviewId;
    private Long resumeId;
    private String provider;
    private String modelName;
    private String configVersion;
    private String promptVersion;
    private String referenceAnswer;
    private String rubricDetail;
}
