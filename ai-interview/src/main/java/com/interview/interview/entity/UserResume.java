package com.interview.interview.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.interview.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 用户可复用的简历版本。
 *
 * <p>简历正文解析成功后保持不可变。用户“更新简历”实际创建新版本，避免历史面试的
 * 个性化评分上下文随文件修改而变化。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("user_resume")
public class UserResume extends BaseEntity {

    public static final int PARSE_PROCESSING = 0;
    public static final int PARSE_SUCCESS = 1;
    public static final int PARSE_FAILED = 2;

    private Long userId;
    private String originalName;
    private String filePath;
    private Long fileSize;
    private String contentType;
    private String contentHash;
    private String parsedText;
    private Integer parseStatus;
    private String parseError;
}
