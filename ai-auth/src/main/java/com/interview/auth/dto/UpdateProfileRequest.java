package com.interview.auth.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 更新用户信息请求
 */
@Data
public class UpdateProfileRequest {

    /**
     * 昵称（可选，最长 50 字符）
     */
    @Size(max = 50, message = "昵称不能超过 50 个字符")
    private String nickname;

    /**
     * 头像 URL（可选）
     */
    @Size(max = 500, message = "头像链接不能超过 500 个字符")
    private String avatar;
}
