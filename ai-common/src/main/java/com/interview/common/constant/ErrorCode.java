package com.interview.common.constant;

import lombok.Getter;
import lombok.AllArgsConstructor;

/**
 * 全局错误码枚举
 * <p>
 * 按模块划分错误码区间：
 * <ul>
 *   <li>200: 成功</li>
 *   <li>400-500: HTTP 标准错误码</li>
 *   <li>1xxx: 用户模块</li>
 *   <li>2xxx: 面试模块</li>
 *   <li>3xxx: AI 模块</li>
 *   <li>4xxx: 收藏/分享模块</li>
 *   <li>5xxx: 管理员模块</li>
 * </ul>
 * </p>
 */
@Getter
@AllArgsConstructor
public enum ErrorCode {

    SUCCESS(200, "成功"),
    BAD_REQUEST(400, "请求参数错误"),
    UNAUTHORIZED(401, "未认证"),
    FORBIDDEN(403, "无权限访问"),
    NOT_FOUND(404, "资源不存在"),
    INTERNAL_ERROR(500, "服务器内部错误"),

    // 用户模块 1xxx
    USERNAME_EXISTS(1001, "用户名已存在"),
    PASSWORD_ERROR(1002, "密码错误"),
    USER_DISABLED(1003, "账户已被禁用"),
    LOGIN_FAILED(1004, "登录失败次数过多，请15分钟后重试"),
    EMAIL_EXISTS(1005, "邮箱已被注册"),
    VERIFY_CODE_ERROR(1006, "验证码错误或已过期"),
    VERIFY_CODE_FREQUENT(1007, "验证码发送过于频繁，请稍后再试"),
    EMAIL_FORMAT_ERROR(1008, "邮箱格式不正确"),

    // 面试模块 2xxx
    AUDIO_FORMAT_NOT_SUPPORTED(2001, "音频文件格式不支持"),
    AUDIO_FILE_TOO_LARGE(2002, "音频文件过大"),
    INTERVIEW_NOT_FOUND(2003, "面试记录不存在"),
    INTERVIEW_ACCESS_DENIED(2004, "面试记录不属于当前用户"),
    INTERVIEW_PROCESSING(2005, "面试记录正在处理中，无法重试"),

    // AI 模块 3xxx
    AI_CONFIG_NOT_FOUND(3001, "AI 模型配置不存在"),
    AI_MODEL_CALL_FAILED(3002, "AI 模型调用失败"),
    PROCESSING_FAILED(3003, "面试处理失败"),

    // 收藏/分享模块 4xxx
    FAVORITE_LIMIT(4001, "收藏数已达上限"),
    FAVORITE_EXISTS(4002, "该问题已收藏"),
    SHARE_EXPIRED(4003, "分享链接已过期"),
    SHARE_NOT_FOUND(4004, "分享链接不存在"),

    // 管理员模块 5xxx
    ADMIN_REQUIRED(5001, "无权限访问，需要管理员权限"),
    USER_DISABLED_BY_ADMIN(5002, "用户已被禁用"),
    EXPERIENCE_NOT_FOUND(5003, "面经已下架或不存在"),
    COMMENT_EMPTY(5004, "评论内容不能为空"),
    COMMENT_TOO_LONG(5005, "评论内容超出长度限制"),
    COMMENT_ACCESS_DENIED(5006, "无权删除该评论");

    private final int code;
    private final String message;
}
