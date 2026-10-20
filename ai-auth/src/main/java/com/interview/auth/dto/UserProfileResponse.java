package com.interview.auth.dto;

import lombok.Builder;
import lombok.Data;

import java.util.Set;

/**
 * 用户资料响应 DTO
 */
@Data
@Builder
public class UserProfileResponse {

    private Long id;
    private String username;
    private String email;
    private String nickname;
    private String avatar;
    private Set<String> roles;
    private Set<String> permissions;
}
