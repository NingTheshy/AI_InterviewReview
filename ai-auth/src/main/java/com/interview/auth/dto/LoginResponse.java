package com.interview.auth.dto;

import lombok.Builder;
import lombok.Data;

/**
 * 登录响应 DTO
 */
@Data
@Builder
public class LoginResponse {

    private String token;
    private String tokenType;
    private long expiresIn;
    private UserInfo user;

    @Data
    @Builder
    public static class UserInfo {
        private Long id;
        private String username;
        private String email;
        private String nickname;
        private String avatar;
        private Integer role;
    }
}
