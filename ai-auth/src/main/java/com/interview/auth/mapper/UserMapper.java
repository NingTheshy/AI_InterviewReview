package com.interview.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.interview.auth.entity.User;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户 Mapper 接口
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {
}
