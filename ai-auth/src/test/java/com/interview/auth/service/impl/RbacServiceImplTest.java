package com.interview.auth.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.interview.auth.entity.Role;
import com.interview.auth.entity.UserRole;
import com.interview.auth.mapper.RbacMapper;
import com.interview.auth.mapper.RoleMapper;
import com.interview.auth.mapper.UserRoleMapper;
import com.interview.auth.service.AuditService;
import com.interview.common.constant.PermissionCodes;
import com.interview.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 完整 RBAC 角色分配和最后管理者保护测试。 */
@ExtendWith(MockitoExtension.class)
class RbacServiceImplTest {

    @Mock private RbacMapper rbacMapper;
    @Mock private RoleMapper roleMapper;
    @Mock private UserRoleMapper userRoleMapper;
    @Mock private AuditService auditService;

    private RbacServiceImpl service;
    private Role userRole;
    private Role adminRole;

    @BeforeEach
    void setUp() {
        service = new RbacServiceImpl(rbacMapper, roleMapper, userRoleMapper, auditService);
        userRole = role(2L, "user");
        adminRole = role(1L, "admin");
    }

    @Test
    @DisplayName("空角色集合自动回退为 user，避免无角色账号")
    void emptyRolesFallsBackToUser() {
        when(rbacMapper.selectRoleCodesByUserId(10L)).thenReturn(List.of());
        when(roleMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(userRole));
        when(rbacMapper.selectPermissionCodesByRoleIds(any())).thenReturn(List.of("interview:read"));

        service.replaceUserRoles(null, 10L, Set.of());

        verify(userRoleMapper).deleteByUserId(10L);
        verify(userRoleMapper).insertRelation(any(UserRole.class));
        verify(auditService).recordSuccess(eq(null), eq("USER_ROLE_ASSIGN"), eq("USER"),
                eq("10"), eq(Set.of()), eq(Set.of("user")));
    }

    @Test
    @DisplayName("分配 admin 时自动同时分配 user")
    void adminAlwaysIncludesUser() {
        when(rbacMapper.selectRoleCodesByUserId(10L)).thenReturn(List.of());
        when(roleMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(adminRole, userRole));
        when(rbacMapper.selectPermissionCodesByRoleIds(any())).thenReturn(List.of(PermissionCodes.USER_ROLE_ASSIGN));

        service.replaceUserRoles(null, 10L, Set.of("admin"));

        verify(userRoleMapper, times(2)).insertRelation(any(UserRole.class));
    }

    @Test
    @DisplayName("禁止操作人修改自己的角色")
    void cannotModifyOwnRoles() {
        when(rbacMapper.selectRoleCodesByUserId(10L)).thenReturn(List.of("user", "admin"));

        assertThrows(BusinessException.class,
                () -> service.replaceUserRoles(10L, 10L, Set.of("user")));

        verify(userRoleMapper, never()).deleteByUserId(anyLong());
        verify(auditService).recordFailure(eq(10L), eq("USER_ROLE_ASSIGN"), eq("USER"),
                eq("10"), any(), any(), any());
    }

    @Test
    @DisplayName("禁止授予超出操作人有效权限范围的角色")
    void cannotGrantBeyondOperatorPermissions() {
        when(rbacMapper.selectRoleCodesByUserId(20L)).thenReturn(List.of("user"));
        when(roleMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(adminRole, userRole));
        when(rbacMapper.selectPermissionCodesByRoleIds(any()))
                .thenReturn(List.of(PermissionCodes.USER_ROLE_ASSIGN, PermissionCodes.AUDIT_READ));
        when(rbacMapper.selectPermissionCodesByUserId(10L))
                .thenReturn(List.of(PermissionCodes.USER_ROLE_ASSIGN));

        assertThrows(BusinessException.class,
                () -> service.replaceUserRoles(10L, 20L, Set.of("user", "admin")));

        verify(userRoleMapper, never()).deleteByUserId(anyLong());
    }

    @Test
    @DisplayName("禁止移除最后一个有效权限管理者的管理权限")
    void protectsLastPermissionManager() {
        when(rbacMapper.selectRoleCodesByUserId(20L)).thenReturn(List.of("user", "admin"));
        when(roleMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(userRole));
        when(rbacMapper.selectPermissionCodesByRoleIds(any())).thenReturn(List.of("interview:read"));
        when(rbacMapper.selectPermissionCodesByUserId(10L))
                .thenReturn(List.of(PermissionCodes.USER_ROLE_ASSIGN, "interview:read"));
        when(rbacMapper.selectPermissionCodesByUserId(20L))
                .thenReturn(List.of(PermissionCodes.USER_ROLE_ASSIGN));
        when(rbacMapper.countActiveUsersWithPermission(PermissionCodes.USER_ROLE_ASSIGN)).thenReturn(1L);

        assertThrows(BusinessException.class,
                () -> service.replaceUserRoles(10L, 20L, Set.of("user")));

        verify(userRoleMapper, never()).deleteByUserId(anyLong());
    }

    @Test
    @DisplayName("禁止禁用最后一个有效权限管理者")
    void protectsLastManagerFromDisable() {
        when(rbacMapper.selectPermissionCodesByUserId(20L))
                .thenReturn(List.of(PermissionCodes.USER_ROLE_ASSIGN));
        when(rbacMapper.countActiveUsersWithPermission(PermissionCodes.USER_ROLE_ASSIGN)).thenReturn(1L);

        assertThrows(BusinessException.class, () -> service.assertCanDisableUser(10L, 20L));

        verify(auditService).recordFailure(eq(10L), eq("USER_STATUS_UPDATE"), eq("USER"),
                eq("20"), any(), any(), any());
    }

    private Role role(Long id, String code) {
        Role role = new Role();
        role.setId(id);
        role.setRoleName(code);
        role.setStatus(1);
        return role;
    }
}
