package com.interview.ai.service;

import com.interview.ai.entity.AiConfig;
import com.interview.common.result.PageResult;

import java.util.List;

/**
 * AI 配置服务接口
 * <p>
 * 提供 AI 模型配置的 CRUD 管理功能。
 * </p>
 */
public interface AiConfigService {

    /**
     * 分页查询配置列表
     *
     * @param page 页码
     * @param size 每页大小
     * @return 分页结果
     */
    PageResult<AiConfig> list(int page, int size);

    /**
     * 获取配置详情
     *
     * @param id 配置 ID
     * @return 配置详情
     */
    AiConfig getDetail(Long id);

    /**
     * 获取指定类型的默认配置
     *
     * @param configType 配置类型（1=ASR, 2=LLM）
     * @return 默认配置，不存在返回 null
     */
    AiConfig getDefaultByConfigType(Integer configType);

    /**
     * 创建配置
     *
     * @param config 配置信息
     */
    void create(AiConfig config);

    /**
     * 更新配置
     *
     * @param id     配置 ID
     * @param config 配置信息
     */
    void update(Long id, AiConfig config);

    /**
     * 删除配置
     *
     * @param id 配置 ID
     */
    void delete(Long id);

    /**
     * 设为默认配置
     *
     * @param id 配置 ID
     */
    void setDefault(Long id);
}
