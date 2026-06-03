package com.interview.common.constant;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 用户状态枚举
 * <p>
 * 表示用户账户的启用/禁用状态，管理员可禁用违规用户。
 * </p>
 */
@Getter
@AllArgsConstructor
public enum UserStatus {

    DISABLED(0, "禁用"),
    ACTIVE(1, "正常");

    private final int code;
    private final String description;
}
