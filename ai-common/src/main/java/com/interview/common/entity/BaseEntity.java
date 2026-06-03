package com.interview.common.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 实体基类
 * <p>
 * 所有数据库实体的父类，提供通用字段：
 * <ul>
 *   <li>id - 主键自增</li>
 *   <li>deleted - 逻辑删除标记</li>
 *   <li>createdAt - 创建时间（自动填充）</li>
 *   <li>updatedAt - 更新时间（自动填充）</li>
 * </ul>
 * </p>
 */
@Data
public abstract class BaseEntity implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableLogic
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
