package com.interview.interview.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 分享请求 DTO
 * <p>
 * 用于生成面试分享链接的请求体。支持设置过期时间和公开状态。
 * </p>
 */
@Data
public class ShareRequest {

    /** 过期类型：24h（24小时）、7d（7天）、30d（30天）、never（永不过期），默认 7d */
    @Size(max = 10, message = "过期类型长度不能超过 10 个字符")
    @Pattern(regexp = "^(24h|7d|30d|never)$", message = "过期类型必须为: 24h, 7d, 30d, never")
    private String expireType;

    /** 是否公开到面经广场，true=公开，false=私有，默认 false */
    private Boolean isPublic;
}
