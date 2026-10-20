package com.interview.auth.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.interview.auth.entity.Role;
import com.interview.auth.entity.UserRole;
import com.interview.auth.mapper.RbacMapper;
import com.interview.auth.mapper.RoleMapper;
import com.interview.auth.mapper.UserRoleMapper;
import com.interview.auth.service.AuditService;
import com.interview.auth.service.RbacService;
import com.interview.common.constant.ErrorCode;
import com.interview.common.constant.PermissionCodes;
import com.interview.common.constant.RoleCodes;
import com.interview.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/** RBAC 服务实现，以数据库关系作为唯一授权来源。 */
@Service
@RequiredArgsConstructor
public class RbacServiceImpl implements RbacService {

    private static final String ROLE_ASSIGN_ACTION = "USER_ROLE_ASSIGN";

    private final RbacMapper rbacMapper;
    private final RoleMapper roleMapper;
    private final UserRoleMapper userRoleMapper;
    private final AuditService auditService;

    @Override
    public Set<String> getRoleCodes(Long userId) {
        return new LinkedHashSet<>(rbacMapper.selectRoleCodesByUserId(userId));
    }

    @Override
    public Set<String> getPermissionCodes(Long userId) {
        return new LinkedHashSet<>(rbacMapper.selectPermissionCodesByUserId(userId));
    }

    @Override
    @Transactional
    public void assignDefaultRole(Long userId) {
        Role userRole = requireRole(RoleCodes.USER);
        insertUserRole(userId, userRole.getId());
    }

    @Override
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void replaceUserRoles(Long operatorUserId, Long targetUserId, Set<String> roleCodes) {
        Set<String> before = getRoleCodes(targetUserId);
        if (operatorUserId != null && operatorUserId.equals(targetUserId)) {
            reject(operatorUserId, targetUserId, before, roleCodes, "不能修改自己的角色");
        }

        Set<String> normalized = normalizeRoleCodes(roleCodes);
        if (normalized.isEmpty()) {
            normalized.add(RoleCodes.USER);
        }
        if (normalized.contains(RoleCodes.ADMIN)) {
            normalized.add(RoleCodes.USER);
        }

        List<Role> roles = roleMapper.selectList(new LambdaQueryWrapper<Role>()
                .in(Role::getRoleName, normalized)
                .eq(Role::getStatus, 1));
        Set<String> foundCodes = roles.stream().map(Role::getRoleName).collect(Collectors.toSet());
        if (!foundCodes.equals(normalized)) {
            reject(operatorUserId, targetUserId, before, normalized, "包含不存在或已禁用的角色");
        }

        Set<String> requestedPermissions = roles.isEmpty()
                ? Set.of()
                : new LinkedHashSet<>(rbacMapper.selectPermissionCodesByRoleIds(
                        roles.stream().map(Role::getId).toList()));
        if (operatorUserId != null && !getPermissionCodes(operatorUserId).containsAll(requestedPermissions)) {
            reject(operatorUserId, targetUserId, before, normalized, "不能授予超出自身权限范围的角色");
        }

        boolean removesPermissionManager = getPermissionCodes(targetUserId)
                .contains(PermissionCodes.USER_ROLE_ASSIGN)
                && !requestedPermissions.contains(PermissionCodes.USER_ROLE_ASSIGN);
        if (removesPermissionManager) {
            assertAnotherPermissionManagerExists(operatorUserId, targetUserId, before, normalized);
        }

        userRoleMapper.deleteByUserId(targetUserId);
        roles.forEach(role -> insertUserRole(targetUserId, role.getId()));
        auditService.recordSuccess(operatorUserId, ROLE_ASSIGN_ACTION, "USER",
                String.valueOf(targetUserId), before, normalized);
    }

    @Override
    public void assertCanDisableUser(Long operatorUserId, Long targetUserId) {
        if (!getPermissionCodes(targetUserId).contains(PermissionCodes.USER_ROLE_ASSIGN)) {
            return;
        }
        if (rbacMapper.countActiveUsersWithPermission(PermissionCodes.USER_ROLE_ASSIGN) <= 1) {
            auditService.recordFailure(operatorUserId, "USER_STATUS_UPDATE", "USER",
                    String.valueOf(targetUserId), "ACTIVE", "DISABLED", "不能禁用最后一个有效权限管理者");
            throw new BusinessException(ErrorCode.FORBIDDEN.getCode(), "不能禁用最后一个有效权限管理者");
        }
    }

    @Override
    public boolean hasEffectivePermissionManager() {
        return rbacMapper.countActiveUsersWithPermission(PermissionCodes.USER_ROLE_ASSIGN) > 0;
    }

    private void assertAnotherPermissionManagerExists(Long operatorUserId, Long targetUserId,
                                                      Set<String> before, Set<String> after) {
        if (rbacMapper.countActiveUsersWithPermission(PermissionCodes.USER_ROLE_ASSIGN) <= 1) {
            reject(operatorUserId, targetUserId, before, after, "不能撤销最后一个有效权限管理者的管理权限");
        }
    }

    private Set<String> normalizeRoleCodes(Set<String> roleCodes) {
        if (roleCodes == null) {
            return new LinkedHashSet<>();
        }
        return roleCodes.stream()
                .filter(code -> code != null && !code.isBlank())
                .map(code -> code.trim().toLowerCase(Locale.ROOT))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private Role requireRole(String code) {
        Role role = roleMapper.selectOne(new LambdaQueryWrapper<Role>()
                .eq(Role::getRoleName, code)
                .eq(Role::getStatus, 1));
        if (role == null) {
            throw new IllegalStateException("缺少系统内置角色: " + code);
        }
        return role;
    }

    private void insertUserRole(Long userId, Long roleId) {
        UserRole relation = new UserRole();
        relation.setUserId(userId);
        relation.setRoleId(roleId);
        relation.setCreatedAt(LocalDateTime.now());
        userRoleMapper.insertRelation(relation);
    }

    private void reject(Long operatorUserId, Long targetUserId, Object before, Object after, String reason) {
        auditService.recordFailure(operatorUserId, ROLE_ASSIGN_ACTION, "USER",
                String.valueOf(targetUserId), before, after, reason);
        throw new BusinessException(ErrorCode.FORBIDDEN.getCode(), reason);
    }
}
