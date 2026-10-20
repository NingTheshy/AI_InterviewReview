package com.interview.ai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.interview.ai.entity.AiConfig;
import com.interview.ai.mapper.AiConfigMapper;
import com.interview.ai.security.AiConfigSecretCipher;
import com.interview.ai.service.AiConfigService;
import com.interview.auth.service.AuditService;
import com.interview.common.constant.ErrorCode;
import com.interview.common.exception.BusinessException;
import com.interview.common.result.PageResult;
import com.interview.common.utils.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

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
    private final AuditService auditService;
    private final AiConfigSecretCipher secretCipher;

    /**
     * {@inheritDoc}
     */
    @Override
    public PageResult<AiConfig> list(int page, int size) {
        Page<AiConfig> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<AiConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByAsc(AiConfig::getSortOrder);

        Page<AiConfig> result = aiConfigMapper.selectPage(pageParam, wrapper);
        result.getRecords().forEach(this::decryptApiKey);
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
        return decryptApiKey(config);
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
        return decryptApiKey(aiConfigMapper.selectOne(wrapper));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public void create(AiConfig config) {
        config.setApiKey(secretCipher.encrypt(config.getApiKey()));
        // 如果设为默认，先取消其他默认
        if (config.getIsDefault() != null && config.getIsDefault() == 1) {
            clearDefault(config.getConfigType());
        }

        aiConfigMapper.insert(config);
        auditService.recordSuccess(SecurityUtils.getCurrentUserId(), "AI_CONFIG_CREATE", "AI_CONFIG",
                String.valueOf(config.getId()), null, safeAuditView(config));
        log.info("创建 AI 配置: name={}", config.getConfigName());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public void update(Long id, AiConfig config) {
        AiConfig existing = aiConfigMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException(ErrorCode.AI_CONFIG_NOT_FOUND.getCode(),
                    ErrorCode.AI_CONFIG_NOT_FOUND.getMessage());
        }

        config.setId(id);
        String existingPlainKey = secretCipher.decrypt(existing.getApiKey());
        String submittedKey = config.getApiKey();
        if (maskApiKey(existingPlainKey).equals(submittedKey)) {
            config.setApiKey(secretCipher.encrypt(existingPlainKey));
        } else {
            config.setApiKey(secretCipher.encrypt(submittedKey));
        }
        if (config.getStatus() == null) {
            config.setStatus(existing.getStatus());
        }

        // 如果设为默认，先取消其他默认
        if (config.getIsDefault() != null && config.getIsDefault() == 1) {
            clearDefault(config.getConfigType());
        }

        aiConfigMapper.updateById(config);
        auditService.recordSuccess(SecurityUtils.getCurrentUserId(), "AI_CONFIG_UPDATE", "AI_CONFIG",
                String.valueOf(id), safeAuditView(existing), safeAuditView(config));
        log.info("更新 AI 配置: id={}", id);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public void delete(Long id) {
        AiConfig config = aiConfigMapper.selectById(id);
        if (config == null) {
            throw new BusinessException(ErrorCode.AI_CONFIG_NOT_FOUND.getCode(),
                    ErrorCode.AI_CONFIG_NOT_FOUND.getMessage());
        }

        aiConfigMapper.deleteById(id);
        auditService.recordSuccess(SecurityUtils.getCurrentUserId(), "AI_CONFIG_DELETE", "AI_CONFIG",
                String.valueOf(id), safeAuditView(config), null);
        log.info("删除 AI 配置: id={}", id);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public void setDefault(Long id) {
        AiConfig config = aiConfigMapper.selectById(id);
        if (config == null) {
            throw new BusinessException(ErrorCode.AI_CONFIG_NOT_FOUND.getCode(),
                    ErrorCode.AI_CONFIG_NOT_FOUND.getMessage());
        }

        Integer beforeDefault = config.getIsDefault();

        // 取消同类型其他默认
        clearDefault(config.getConfigType());

        // 设置当前为默认
        config.setIsDefault(1);
        aiConfigMapper.updateById(config);
        auditService.recordSuccess(SecurityUtils.getCurrentUserId(), "AI_CONFIG_SET_DEFAULT", "AI_CONFIG",
                String.valueOf(id), Map.of("isDefault", beforeDefault == null ? 0 : beforeDefault),
                Map.of("isDefault", 1));

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

    /**
     * 生成不含 API Key 的审计快照，避免凭据进入审计表和日志链路。
     */
    private Map<String, Object> safeAuditView(AiConfig config) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", config.getId());
        view.put("configName", config.getConfigName());
        view.put("provider", config.getProvider());
        view.put("modelName", config.getModelName());
        view.put("configType", config.getConfigType());
        view.put("isDefault", config.getIsDefault());
        view.put("sortOrder", config.getSortOrder());
        view.put("status", config.getStatus());
        return view;
    }

    private AiConfig decryptApiKey(AiConfig config) {
        if (config != null) {
            config.setApiKey(secretCipher.decrypt(config.getApiKey()));
        }
        return config;
    }

    private String maskApiKey(String apiKey) {
        if (apiKey == null || apiKey.length() <= 4) {
            return "****";
        }
        return "****" + apiKey.substring(apiKey.length() - 4);
    }
}
