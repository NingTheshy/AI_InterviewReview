package com.interview.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Set;

/**
 * 用户角色更新请求
 * <p>
 * 管理员修改用户角色时使用的请求体。
 * </p>
 */
@Data
public class UserRoleRequest {

    /** 角色编码集合；空集合会由服务端回退为内置 user 角色。 */
    @NotNull(message = "角色集合不能为空")
    @Size(max = 20, message = "一次最多分配20个角色")
    private Set<@NotBlank(message = "角色编码不能为空") String> roles;
}
