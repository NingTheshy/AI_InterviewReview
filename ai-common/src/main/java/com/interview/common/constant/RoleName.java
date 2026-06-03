package com.interview.common.constant;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 用户角色枚举
 * <p>
 * 定义系统支持的用户角色，用于权限控制。
 * </p>
 */
@Getter
@AllArgsConstructor
public enum RoleName {

    ADMIN("admin", "管理员"),
    USER("user", "普通用户");

    private final String code;
    private final String description;
}
