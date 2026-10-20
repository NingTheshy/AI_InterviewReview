package com.interview.auth.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.interview.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 用户实体类
 * <p>
 * 对应 user 表，存储用户基本信息和登录状态。角色通过 user_role 关联表维护。
 * </p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("user")
public class User extends BaseEntity {

    private String username;
    private String password;
    private String nickname;
    private String email;
    private String avatar;
    private Integer status;
    private LocalDateTime lastLoginAt;
    private Integer loginCount;
}
