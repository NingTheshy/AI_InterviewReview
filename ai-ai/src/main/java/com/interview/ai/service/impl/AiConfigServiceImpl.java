package com.interview.ai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.interview.ai.entity.AiConfig;
import com.interview.ai.mapper.AiConfigMapper;
import com.interview.ai.service.AiConfigService;
import com.interview.common.constant.ErrorCode;
import com.interview.common.exception.BusinessException;
import com.interview.common.result.PageResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * AI 配置服务实现类
 * <p>
 * 实现 AI 模型配置的 CRUD 管理，支持设置默认配置。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiConfigServiceImpl implements AiConfigService {

    private final AiConfigMapper aiConfigMapper;

    /**
     * {@inheritDoc}
     */
    @Override
    public PageResult<AiConfig> list(int page, int size) {
        Page<AiConfig> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<AiConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByAsc(AiConfig::getSortOrder);

        Page<AiConfig> result = aiConfigMapper.selectPage(pageParam, wrapper);
        return new PageResult<>(result.getRecords(), result.getTotal(), page, size);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public AiConfig getDetail(Long id) {
        AiConfig config = aiConfigMapper.selectById(id);
        if (config == null) {
            throw new BusinessException(ErrorCode.AI_CONFIG_NOT_FOUND.getCode(),
                    ErrorCode.AI_CONFIG_NOT_FOUND.getMessage());
        }
        return config;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public AiConfig getDefaultByConfigType(Integer configType) {
        LambdaQueryWrapper<AiConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AiConfig::getConfigType, configType);
        wrapper.eq(AiConfig::getIsDefault, 1);
        wrapper.eq(AiConfig::getStatus, 1);
        return aiConfigMapper.selectOne(wrapper);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void create(AiConfig config) {
        // 如果设为默认，先取消其他默认
        if (config.getIsDefault() != null && config.getIsDefault() == 1) {
            clearDefault(config.getConfigType());
        }

        aiConfigMapper.insert(config);
        log.info("创建 AI 配置: name={}", config.getConfigName());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void update(Long id, AiConfig config) {
        AiConfig existing = aiConfigMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException(ErrorCode.AI_CONFIG_NOT_FOUND.getCode(),
                    ErrorCode.AI_CONFIG_NOT_FOUND.getMessage());
        }

        config.setId(id);

        // 如果设为默认，先取消其他默认
        if (config.getIsDefault() != null && config.getIsDefault() == 1) {
            clearDefault(config.getConfigType());
        }

        aiConfigMapper.updateById(config);
        log.info("更新 AI 配置: id={}", id);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void delete(Long id) {
        AiConfig config = aiConfigMapper.selectById(id);
        if (config == null) {
            throw new BusinessException(ErrorCode.AI_CONFIG_NOT_FOUND.getCode(),
                    ErrorCode.AI_CONFIG_NOT_FOUND.getMessage());
        }

        aiConfigMapper.deleteById(id);
        log.info("删除 AI 配置: id={}", id);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setDefault(Long id) {
        AiConfig config = aiConfigMapper.selectById(id);
        if (config == null) {
            throw new BusinessException(ErrorCode.AI_CONFIG_NOT_FOUND.getCode(),
                    ErrorCode.AI_CONFIG_NOT_FOUND.getMessage());
        }

        // 取消同类型其他默认
        clearDefault(config.getConfigType());

        // 设置当前为默认
        config.setIsDefault(1);
        aiConfigMapper.updateById(config);

        log.info("设置默认 AI 配置: id={}, type={}", id, config.getConfigType());
    }

    /**
     * 清除同类型其他默认配置
     *
     * @param configType 配置类型
     */
    private void clearDefault(Integer configType) {
        LambdaQueryWrapper<AiConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AiConfig::getConfigType, configType);
        wrapper.eq(AiConfig::getIsDefault, 1);

        AiConfig defaultConfig = aiConfigMapper.selectOne(wrapper);
        if (defaultConfig != null) {
            defaultConfig.setIsDefault(0);
            aiConfigMapper.updateById(defaultConfig);
        }
    }
}
