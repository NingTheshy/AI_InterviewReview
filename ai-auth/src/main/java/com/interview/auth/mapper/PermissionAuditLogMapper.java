package com.interview.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.interview.auth.entity.PermissionAuditLog;
import org.apache.ibatis.annotations.Mapper;

/** 权限审计日志数据访问接口。 */
@Mapper
public interface PermissionAuditLogMapper extends BaseMapper<PermissionAuditLog> {
}
