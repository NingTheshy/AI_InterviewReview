package com.interview.common.constant;

/**
 * RBAC 权限码常量。
 *
 * <p>权限码描述可独立授予的业务能力，不与某个 URL 或 HTTP 方法绑定。</p>
 */
public final class PermissionCodes {

    public static final String INTERVIEW_CREATE = "interview:create";
    public static final String INTERVIEW_READ = "interview:read";
    public static final String INTERVIEW_MANAGE = "interview:manage";
    public static final String NOTE_MANAGE = "note:manage";
    public static final String FAVORITE_MANAGE = "favorite:manage";
    public static final String EXPERIENCE_PUBLISH = "experience:publish";
    public static final String COMMENT_MANAGE_OWN = "comment:manage_own";

    public static final String USER_READ = "user:read";
    public static final String USER_STATUS_UPDATE = "user:status:update";
    public static final String USER_ROLE_ASSIGN = "user:role:assign";
    public static final String INTERVIEW_READ_ALL = "interview:read_all";
    public static final String EXPERIENCE_READ_ALL = "experience:read_all";
    public static final String EXPERIENCE_MANAGE = "experience:manage";
    public static final String COMMENT_READ_ALL = "comment:read_all";
    public static final String COMMENT_MANAGE = "comment:manage";
    public static final String AI_CONFIG_READ = "ai_config:read";
    public static final String AI_CONFIG_MANAGE = "ai_config:manage";
    public static final String SYSTEM_STATS_READ = "system:stats:read";
    public static final String SYSTEM_MONITOR_READ = "system:monitor:read";
    public static final String AUDIT_READ = "audit:read";

    private PermissionCodes() {
    }
}
