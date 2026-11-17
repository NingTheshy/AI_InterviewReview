package com.interview.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.interview.auth.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 用户 Mapper 接口
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {
    @Select("SELECT * FROM user WHERE id = #{id} AND deleted = 0 FOR UPDATE")
    User selectByIdForUpdate(@Param("id") Long id);
}
