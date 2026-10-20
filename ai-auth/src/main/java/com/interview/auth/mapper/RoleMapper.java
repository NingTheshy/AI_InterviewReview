package com.interview.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.interview.auth.entity.Role;
import org.apache.ibatis.annotations.Mapper;

/** 角色数据访问接口。 */
@Mapper
public interface RoleMapper extends BaseMapper<Role> {
}
