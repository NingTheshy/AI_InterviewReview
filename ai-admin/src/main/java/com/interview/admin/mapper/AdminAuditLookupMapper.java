package com.interview.admin.mapper;

import lombok.Data;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;
import java.util.List;

/** 批量读取审计目标的当前名称；已删除目标由审计快照兜底。 */
@Mapper
public interface AdminAuditLookupMapper {

    @Select("<script>SELECT id, config_name AS configName FROM ai_config WHERE id IN "
            + "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach></script>")
    List<ConfigLabel> selectConfigLabels(@Param("ids") Collection<Long> ids);

    @Data
    class ConfigLabel {
        private Long id;
        private String configName;
    }
}
