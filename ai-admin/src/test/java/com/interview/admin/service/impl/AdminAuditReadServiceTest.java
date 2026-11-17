package com.interview.admin.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.interview.admin.dto.AdminAuditLogResponse;
import com.interview.admin.mapper.AdminAuditLookupMapper;
import com.interview.auth.entity.PermissionAuditLog;
import com.interview.auth.entity.User;
import com.interview.auth.mapper.UserMapper;
import com.interview.auth.service.AuditService;
import com.interview.common.result.PageResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 审计表格应展示可读含义，同时保留原始记录。 */
@ExtendWith(MockitoExtension.class)
class AdminAuditReadServiceTest {

    @Mock private AuditService auditService;
    @Mock private UserMapper userMapper;
    @Mock private AdminAuditLookupMapper lookupMapper;
    @Spy private ObjectMapper objectMapper = new ObjectMapper();
    @InjectMocks private AdminAuditReadService service;

    @Test
    void resolvesOperatorTargetAndStatusChange() {
        PermissionAuditLog log = log(6L, 1L, "USER_STATUS_UPDATE", "USER", "2", "1", "0");
        User admin = user(1L, "admin", "张管理员");
        User target = user(2L, "candidate", "求职者");
        when(auditService.list(1, 20)).thenReturn(new PageResult<>(List.of(log), 1, 1, 20));
        when(userMapper.selectBatchIds(Set.of(1L, 2L))).thenReturn(List.of(admin, target));

        AdminAuditLogResponse row = service.list(1, 20).getRecords().get(0);

        assertEquals("张管理员", row.getOperatorName());
        assertEquals("admin", row.getOperatorUsername());
        assertEquals("求职者", row.getTargetName());
        assertEquals("停用用户", row.getActionText());
        assertEquals("将「求职者」的账号状态由启用改为停用", row.getDescription());
        assertEquals("USER_STATUS_UPDATE", row.getAction());
        assertEquals(6L, row.getId());
    }

    @Test
    void resolvesConfigNameForDefaultChange() {
        PermissionAuditLog log = log(7L, null, "AI_CONFIG_SET_DEFAULT", "AI_CONFIG", "3",
                "{\"isDefault\":0}", "{\"isDefault\":1}");
        AdminAuditLookupMapper.ConfigLabel config = new AdminAuditLookupMapper.ConfigLabel();
        config.setId(3L);
        config.setConfigName("千问语音识别");
        when(auditService.list(1, 20)).thenReturn(new PageResult<>(List.of(log), 1, 1, 20));
        when(lookupMapper.selectConfigLabels(anyCollection())).thenReturn(List.of(config));

        AdminAuditLogResponse row = service.list(1, 20).getRecords().get(0);

        assertEquals("系统任务", row.getOperatorName());
        assertEquals("千问语音识别", row.getTargetName());
        assertEquals("设置默认 AI 配置", row.getActionText());
        assertEquals("将「千问语音识别」设为默认配置", row.getDescription());
        verify(lookupMapper).selectConfigLabels(Set.of(3L));
    }

    @Test
    void usesAuditSnapshotWhenActorAndConfigWereDeleted() {
        PermissionAuditLog log = log(8L, 9L, "AI_CONFIG_DELETE", "AI_CONFIG", "4",
                "{\"configName\":\"旧模型配置\"}", null);
        when(auditService.list(1, 20)).thenReturn(new PageResult<>(List.of(log), 1, 1, 20));
        when(userMapper.selectBatchIds(Set.of(9L))).thenReturn(List.of());
        when(lookupMapper.selectConfigLabels(Set.of(4L))).thenReturn(List.of());

        AdminAuditLogResponse row = service.list(1, 20).getRecords().get(0);

        assertEquals("已删除用户", row.getOperatorName());
        assertEquals(9L, row.getOperatorUserId());
        assertEquals("旧模型配置", row.getTargetName());
        assertEquals("删除了「旧模型配置」", row.getDescription());
    }

    private PermissionAuditLog log(Long id, Long operatorId, String action, String targetType,
                                   String targetId, String before, String after) {
        PermissionAuditLog log = new PermissionAuditLog();
        log.setId(id);
        log.setOperatorUserId(operatorId);
        log.setAction(action);
        log.setTargetType(targetType);
        log.setTargetId(targetId);
        log.setBeforeData(before);
        log.setAfterData(after);
        log.setSuccess(1);
        return log;
    }

    private User user(Long id, String username, String nickname) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setNickname(nickname);
        return user;
    }
}
