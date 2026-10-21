package com.interview.interview.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 收藏请求 DTO
 * <p>
 * 用于收藏具体面试问题的请求体。
 * 同一用户不能重复收藏同一面试/问题。
 * </p>
 */
@Data
public class FavoriteRequest {

    /** 面试 ID，必填 */
    @NotNull(message = "面试 ID 不能为空")
    private Long interviewId;

    /** 问题 ID，必填；收藏仅支持具体问题。 */
    @NotNull(message = "问题 ID 不能为空")
    private Long questionId;

    /** 收藏备注，如"系统设计典型题"，最大 200 字符 */
    @Size(max = 200, message = "备注不能超过 200 个字符")
    private String remark;
}
