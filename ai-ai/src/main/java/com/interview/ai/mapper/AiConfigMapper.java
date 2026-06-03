package com.interview.ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.interview.ai.entity.AiConfig;
import org.apache.ibatis.annotations.Mapper;

/**
 * AI 配置 Mapper 接口
 */
@Mapper
public interface AiConfigMapper extends BaseMapper<AiConfig> {
}
