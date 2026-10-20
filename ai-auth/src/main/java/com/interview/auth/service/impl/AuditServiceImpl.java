package com.interview.auth.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.interview.auth.entity.PermissionAuditLog;
import com.interview.auth.mapper.PermissionAuditLogMapper;
import com.interview.auth.service.AuditService;
import com.interview.common.result.PageResult;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;

/** 审计日志服务实现。 */
@Service
@RequiredArgsConstructor
public class AuditServiceImpl implements AuditService {

    private final PermissionAuditLogMapper auditLogMapper;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public void recordSuccess(Long operatorUserId, String action, String targetType,
                              String targetId, Object beforeData, Object afterData) {
        insert(operatorUserId, action, targetType, targetId, beforeData, afterData, true, null);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(Long operatorUserId, String action, String targetType,
                              String targetId, Object beforeData, Object afterData, String reason) {
        insert(operatorUserId, action, targetType, targetId, beforeData, afterData, false, reason);
    }

    @Override
    public PageResult<PermissionAuditLog> list(int page, int size) {
        Page<PermissionAuditLog> result = auditLogMapper.selectPage(
                new Page<>(page, size),
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<PermissionAuditLog>()
                        .orderByDesc(PermissionAuditLog::getCreatedAt));
        return new PageResult<>(result.getRecords(), result.getTotal(), page, size);
    }

    private void insert(Long operatorUserId, String action, String targetType, String targetId,
                        Object beforeData, Object afterData, boolean success, String reason) {
        PermissionAuditLog log = new PermissionAuditLog();
        log.setOperatorUserId(operatorUserId);
        log.setAction(action);
        log.setTargetType(targetType);
        log.setTargetId(targetId);
        log.setBeforeData(toJson(beforeData));
        log.setAfterData(toJson(afterData));
        log.setSourceIp(resolveSourceIp());
        log.setSuccess(success ? 1 : 0);
        log.setFailureReason(reason);
        log.setCreatedAt(LocalDateTime.now());
        auditLogMapper.insert(log);
    }

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            return "{\"serializationError\":true}";
        }
    }

    private String resolveSourceIp() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) {
            return null;
        }
        HttpServletRequest request = attributes.getRequest();
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
