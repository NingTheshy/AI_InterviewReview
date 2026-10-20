package com.interview.auth.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;
import java.util.List;

/** RBAC 关联查询接口。 */
@Mapper
public interface RbacMapper {

    @Select("""
            SELECT DISTINCT r.role_name
            FROM user_role ur
            JOIN role r ON r.id = ur.role_id
            WHERE ur.user_id = #{userId} AND r.status = 1 AND r.deleted = 0
            ORDER BY r.id
            """)
    List<String> selectRoleCodesByUserId(@Param("userId") Long userId);

    @Select("""
            SELECT DISTINCT p.permission_name
            FROM user_role ur
            JOIN role r ON r.id = ur.role_id
            JOIN role_permission rp ON rp.role_id = r.id
            JOIN permission p ON p.id = rp.permission_id
            WHERE ur.user_id = #{userId} AND r.status = 1 AND r.deleted = 0
            ORDER BY p.id
            """)
    List<String> selectPermissionCodesByUserId(@Param("userId") Long userId);

    @Select("""
            <script>
            SELECT DISTINCT p.permission_name
            FROM role_permission rp
            JOIN permission p ON p.id = rp.permission_id
            WHERE rp.role_id IN
            <foreach collection="roleIds" item="roleId" open="(" separator="," close=")">
                #{roleId}
            </foreach>
            </script>
            """)
    List<String> selectPermissionCodesByRoleIds(@Param("roleIds") Collection<Long> roleIds);

    @Select("""
            SELECT COUNT(DISTINCT u.id)
            FROM user u
            JOIN user_role ur ON ur.user_id = u.id
            JOIN role r ON r.id = ur.role_id
            JOIN role_permission rp ON rp.role_id = r.id
            JOIN permission p ON p.id = rp.permission_id
            WHERE u.status = 1 AND u.deleted = 0
              AND r.status = 1 AND r.deleted = 0
              AND p.permission_name = #{permissionCode}
            """)
    long countActiveUsersWithPermission(@Param("permissionCode") String permissionCode);
}
