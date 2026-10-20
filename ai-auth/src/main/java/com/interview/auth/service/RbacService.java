package com.interview.auth.service;

import java.util.Set;

/** RBAC 权限解析和用户角色管理服务。 */
public interface RbacService {

    Set<String> getRoleCodes(Long userId);

    Set<String> getPermissionCodes(Long userId);

    void assignDefaultRole(Long userId);

    void replaceUserRoles(Long operatorUserId, Long targetUserId, Set<String> roleCodes);

    void assertCanDisableUser(Long operatorUserId, Long targetUserId);

    boolean hasEffectivePermissionManager();
}
