package com.interview.auth.dto;

import jakarta.validation.constraints.Null;
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
     * 旧客户端的头像字段；只允许通过专用上传接口修改。
     */
    @Null(message = "请通过头像上传接口修改头像")
    private String avatar;
}
