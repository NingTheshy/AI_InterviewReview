package com.interview.auth.mapper;

import com.interview.auth.entity.UserRole;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 用户角色关联数据访问接口。 */
@Mapper
public interface UserRoleMapper {

    /** 删除指定用户的全部角色关系。 */
    @Delete("DELETE FROM user_role WHERE user_id = #{userId}")
    int deleteByUserId(@Param("userId") Long userId);

    /** 新增一条用户角色关系；联合主键由数据库保证不重复。 */
    @Insert("INSERT INTO user_role (user_id, role_id, created_at) "
            + "VALUES (#{userId}, #{roleId}, #{createdAt})")
    int insertRelation(UserRole relation);
}
