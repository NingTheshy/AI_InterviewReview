package com.interview.auth.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.interview.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 系统角色实体。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("role")
public class Role extends BaseEntity {

    private String roleName;
    private String roleLabel;
    private Integer status;
    private Integer isSystem;
}
