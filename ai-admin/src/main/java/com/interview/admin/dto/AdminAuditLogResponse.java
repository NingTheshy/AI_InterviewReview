package com.interview.admin.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/** 管理端审计日志展示对象；保留原始字段，便于追溯。 */
@Data
@Builder
public class AdminAuditLogResponse {

    private Long id;
    private Long operatorUserId;
    private String operatorName;
    private String operatorUsername;
    private String action;
    private String actionText;
    private String description;
    private String targetType;
    private String targetId;
    private String targetName;
    private String targetAccount;
    private String beforeData;
    private String afterData;
    private String sourceIp;
    private Integer success;
    private String failureReason;
    private LocalDateTime createdAt;
}
