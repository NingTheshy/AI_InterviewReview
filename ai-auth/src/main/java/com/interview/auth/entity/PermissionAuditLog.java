package com.interview.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 权限与高风险管理操作审计日志。 */
@Data
@TableName("permission_audit_log")
public class PermissionAuditLog {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long operatorUserId;
    private String action;
    private String targetType;
    private String targetId;
    private String beforeData;
    private String afterData;
    private String sourceIp;
    private Integer success;
    private String failureReason;
    private LocalDateTime createdAt;
}
