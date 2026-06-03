package com.interview.admin.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 用户角色更新请求
 * <p>
 * 管理员修改用户角色时使用的请求体。
 * </p>
 */
@Data
public class UserRoleRequest {

    /**
     * 用户角色：0=普通用户, 1=管理员
     */
    @NotNull(message = "角色不能为空")
    @Min(value = 0, message = "角色值无效，0=普通用户, 1=管理员")
    @Max(value = 1, message = "角色值无效，0=普通用户, 1=管理员")
    private Integer role;
}
