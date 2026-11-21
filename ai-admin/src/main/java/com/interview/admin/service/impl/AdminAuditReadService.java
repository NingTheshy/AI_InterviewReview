package com.interview.admin.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.interview.admin.dto.AdminAuditLogResponse;
import com.interview.admin.mapper.AdminAuditLookupMapper;
import com.interview.auth.entity.PermissionAuditLog;
import com.interview.auth.entity.User;
import com.interview.auth.mapper.UserMapper;
import com.interview.auth.service.AuditService;
import com.interview.common.result.PageResult;
import com.interview.common.constant.ErrorCode;
import com.interview.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 为审计记录补充可读名称和操作说明，不修改原始审计数据。 */
@Service
@RequiredArgsConstructor
public class AdminAuditReadService {

    private final AuditService auditService;
    private final UserMapper userMapper;
    private final AdminAuditLookupMapper lookupMapper;
    private final ObjectMapper objectMapper;

    public PageResult<AdminAuditLogResponse> list(int page, int size) {
        if (page < 1 || (size != 10 && size != 20 && size != 50)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(), "分页参数不合法，仅支持每页10、20或50条");
        }
        PageResult<PermissionAuditLog> rawPage = auditService.list(page, size);
        List<PermissionAuditLog> logs = rawPage.getRecords();
        if (logs.isEmpty()) {
            return new PageResult<>(List.of(), rawPage.getTotal(), page, size);
        }

        Set<Long> userIds = new LinkedHashSet<>();
        Set<Long> configIds = new LinkedHashSet<>();
        for (PermissionAuditLog log : logs) {
            if (log.getOperatorUserId() != null) {
                userIds.add(log.getOperatorUserId());
            }
            Long targetId = numericId(log.getTargetId());
            if (targetId != null && "USER".equals(log.getTargetType())) {
                userIds.add(targetId);
            } else if (targetId != null && "AI_CONFIG".equals(log.getTargetType())) {
                configIds.add(targetId);
            }
        }

        Map<Long, User> users = userIds.isEmpty() ? Collections.emptyMap()
                : userMapper.selectBatchIds(userIds).stream()
                        .collect(Collectors.toMap(User::getId, Function.identity()));
        Map<Long, String> configs = configIds.isEmpty() ? Collections.emptyMap()
                : lookupMapper.selectConfigLabels(configIds).stream()
                        .collect(Collectors.toMap(AdminAuditLookupMapper.ConfigLabel::getId,
                                AdminAuditLookupMapper.ConfigLabel::getConfigName));

        List<AdminAuditLogResponse> records = logs.stream()
                .map(log -> toResponse(log, users, configs))
                .toList();
        return new PageResult<>(records, rawPage.getTotal(), page, size);
    }

    private AdminAuditLogResponse toResponse(PermissionAuditLog log, Map<Long, User> users,
                                              Map<Long, String> configs) {
        User operator = users.get(log.getOperatorUserId());
        Long targetId = numericId(log.getTargetId());
        User targetUser = "USER".equals(log.getTargetType()) ? users.get(targetId) : null;
        JsonNode before = parse(log.getBeforeData());
        JsonNode after = parse(log.getAfterData());
        String targetName = targetName(log, targetUser, configs.get(targetId), before, after);
        String actionText = actionText(log.getAction(), after);
        return AdminAuditLogResponse.builder()
                .id(log.getId())
                .operatorUserId(log.getOperatorUserId())
                .operatorName(log.getOperatorUserId() == null ? "系统任务"
                        : operator == null ? "已删除用户" : displayName(operator))
                .operatorUsername(operator == null ? null : operator.getUsername())
                .action(log.getAction())
                .actionText(actionText)
                .description(description(log, targetName, before, after))
                .targetType(log.getTargetType())
                .targetId(log.getTargetId())
                .targetName(targetName)
                .targetAccount(targetUser == null ? null : targetUser.getUsername())
                .beforeData(log.getBeforeData())
                .afterData(log.getAfterData())
                .sourceIp(log.getSourceIp())
                .success(log.getSuccess())
                .failureReason(log.getFailureReason())
                .createdAt(log.getCreatedAt())
                .build();
    }

    private String targetName(PermissionAuditLog log, User targetUser, String configName,
                              JsonNode before, JsonNode after) {
        return switch (log.getTargetType()) {
            case "USER" -> targetUser == null ? "已删除用户" : displayName(targetUser);
            case "AI_CONFIG" -> firstNonBlank(text(after.path("configName")),
                    text(before.path("configName")), configName, "已删除的 AI 配置");
            case "INTERVIEW_EXPERIENCE" -> "面经 #" + log.getTargetId();
            case "INTERVIEW" -> "面试 #" + log.getTargetId();
            case "INTERVIEW_COMMENT" -> "评论 #" + log.getTargetId();
            default -> "资源 #" + log.getTargetId();
        };
    }

    private String actionText(String action, JsonNode after) {
        return switch (action) {
            case "USER_STATUS_UPDATE" -> switch (status(after)) {
                case "0" -> "停用用户";
                case "1" -> "启用用户";
                default -> "修改用户状态";
            };
            case "USER_ROLE_ASSIGN" -> "调整用户角色";
            case "AI_CONFIG_CREATE" -> "新增 AI 配置";
            case "AI_CONFIG_UPDATE" -> "修改 AI 配置";
            case "AI_CONFIG_DELETE" -> "删除 AI 配置";
            case "AI_CONFIG_SET_DEFAULT" -> "设置默认 AI 配置";
            case "EXPERIENCE_STATUS_UPDATE" -> switch (text(after)) {
                case "BLOCKED" -> "下架面经";
                case "NORMAL" -> "恢复面经";
                default -> "修改面经状态";
            };
            case "COMMENT_DELETE" -> "删除评论";
            case "INTERVIEW_ADMIN_CANCEL" -> "取消面试处理";
            case "INTERVIEW_ADMIN_RETRY" -> "重试面试处理";
            case "INTERVIEW_ADMIN_EDIT" -> "编辑面试基础信息";
            case "INTERVIEW_ADMIN_DELETE" -> "将面试移入回收站";
            case "INTERVIEW_ADMIN_RESTORE" -> "恢复面试";
            default -> "其他管理操作";
        };
    }

    private String description(PermissionAuditLog log, String targetName, JsonNode before, JsonNode after) {
        return switch (log.getAction()) {
            case "USER_STATUS_UPDATE" -> "将「" + targetName + "」的账号状态由"
                    + statusLabel(before) + "改为" + statusLabel(after);
            case "USER_ROLE_ASSIGN" -> "将「" + targetName + "」的角色由"
                    + rolesLabel(before) + "调整为" + rolesLabel(after);
            case "AI_CONFIG_CREATE" -> "创建了「" + targetName + "」";
            case "AI_CONFIG_UPDATE" -> "修改了「" + targetName + "」的配置";
            case "AI_CONFIG_DELETE" -> "删除了「" + targetName + "」";
            case "AI_CONFIG_SET_DEFAULT" -> "将「" + targetName + "」设为默认配置";
            case "EXPERIENCE_STATUS_UPDATE" -> "将" + targetName + "由"
                    + moderationLabel(before) + "改为" + moderationLabel(after);
            case "COMMENT_DELETE" -> "删除了" + targetName;
            case "INTERVIEW_ADMIN_CANCEL" -> "取消了" + targetName + "的处理任务";
            case "INTERVIEW_ADMIN_RETRY" -> "重新提交了" + targetName + "的处理任务";
            case "INTERVIEW_ADMIN_EDIT" -> "修改了" + targetName + "的基础信息";
            case "INTERVIEW_ADMIN_DELETE" -> "将" + targetName + "移入回收站";
            case "INTERVIEW_ADMIN_RESTORE" -> "恢复了" + targetName;
            default -> "操作代码：" + log.getAction();
        };
    }

    private JsonNode parse(String json) {
        if (json == null || json.isBlank()) {
            return objectMapper.getNodeFactory().nullNode();
        }
        try {
            return objectMapper.readTree(json);
        } catch (Exception ignored) {
            return objectMapper.getNodeFactory().nullNode();
        }
    }

    private String status(JsonNode node) {
        String value = text(node);
        return switch (value) {
            case "ACTIVE" -> "1";
            case "DISABLED" -> "0";
            default -> value;
        };
    }

    private String statusLabel(JsonNode node) {
        return switch (status(node)) {
            case "1" -> "启用";
            case "0" -> "停用";
            default -> "未知状态";
        };
    }

    private String moderationLabel(JsonNode node) {
        return switch (text(node)) {
            case "NORMAL" -> "正常展示";
            case "BLOCKED" -> "违规下架";
            default -> "未知状态";
        };
    }

    private String rolesLabel(JsonNode node) {
        if (!node.isArray()) {
            return "未知角色";
        }
        List<String> names = new ArrayList<>();
        node.forEach(role -> names.add(switch (text(role).toLowerCase()) {
            case "user" -> "普通用户";
            case "admin" -> "管理员";
            default -> text(role);
        }));
        return names.isEmpty() ? "无角色" : String.join("、", names);
    }

    private String displayName(User user) {
        return firstNonBlank(user.getNickname(), user.getUsername(), "未命名用户");
    }

    private String text(JsonNode node) {
        return node == null || node.isNull() || node.isMissingNode() ? "" : node.asText();
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private Long numericId(String value) {
        try {
            return value == null ? null : Long.valueOf(value);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
