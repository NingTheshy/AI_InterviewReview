package com.interview.auth.service;

import com.interview.auth.entity.PermissionAuditLog;
import com.interview.common.result.PageResult;

/** 权限与高风险操作审计服务。 */
public interface AuditService {

    void recordSuccess(Long operatorUserId, String action, String targetType,
                       String targetId, Object beforeData, Object afterData);

    void recordFailure(Long operatorUserId, String action, String targetType,
                       String targetId, Object beforeData, Object afterData, String reason);

    PageResult<PermissionAuditLog> list(int page, int size);
}
