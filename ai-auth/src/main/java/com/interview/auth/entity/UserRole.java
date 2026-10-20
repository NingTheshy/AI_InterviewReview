package com.interview.auth.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 用户与角色的多对多关联。 */
@Data
@TableName("user_role")
public class UserRole {

    private Long userId;
    private Long roleId;
    private LocalDateTime createdAt;
}
