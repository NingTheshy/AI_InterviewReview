package com.interview.admin.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户管理响应
 * <p>
 * 管理员查看用户列表和详情时使用的响应对象。
 * 包含用户基本信息、角色、状态和登录统计。
 * </p>
 */
@Data
@Builder
public class UserManagementResponse {

    /** 用户 ID */
    private Long id;

    /** 用户名 */
    private String username;

    /** 邮箱 */
    private String email;

    /** 昵称 */
    private String nickname;

    /** 角色：0=普通用户, 1=管理员 */
    private Integer role;

    /** 状态：0=禁用, 1=正常 */
    private Integer status;

    /** 登录次数 */
    private Integer loginCount;

    /** 最后登录时间 */
    private LocalDateTime lastLoginAt;

    /** 创建时间 */
    private LocalDateTime createdAt;
}
