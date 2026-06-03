package com.interview.admin.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 用户状态更新请求
 * <p>
 * 管理员启用或禁用用户时使用的请求体。
 * </p>
 */
@Data
public class UserStatusRequest {

    /**
     * 用户状态：0=禁用, 1=正常
     */
    @NotNull(message = "状态不能为空")
    @Min(value = 0, message = "状态值无效，0=禁用, 1=正常")
    @Max(value = 1, message = "状态值无效，0=禁用, 1=正常")
    private Integer status;
}
