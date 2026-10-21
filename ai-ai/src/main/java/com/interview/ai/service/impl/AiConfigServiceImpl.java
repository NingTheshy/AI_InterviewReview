package com.interview.ai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.interview.ai.entity.AiConfig;
import com.interview.ai.mapper.AiConfigMapper;
import com.interview.ai.security.AiConfigSecretCipher;
import com.interview.ai.factory.AiClientFactory;
import com.interview.ai.service.AsrClient;
import com.interview.ai.service.LlmClient;
import com.interview.ai.service.WholeFileAsrClient;
import com.interview.ai.service.AiConfigService;
import com.interview.auth.service.AuditService;
import com.interview.common.constant.ErrorCode;
import com.interview.common.exception.BusinessException;
import com.interview.common.result.PageResult;
import com.interview.common.utils.SecurityUtils;
import com.interview.common.utils.FileUtil;
import com.interview.common.constant.ConfigType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.net.URI;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
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
    private final AiClientFactory clientFactory;

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
        validate(config);
        if (Integer.valueOf(1).equals(config.getIsDefault())) {
            throw new BusinessException(ErrorCode.AI_CONFIG_NOT_TESTED.getCode(), "新配置测试成功后才能设为默认");
        }
        config.setIsDefault(0);
        config.setTestStatus(0);
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
        validate(config);
        String newFingerprint = fingerprint(config, secretCipher.decrypt(config.getApiKey()));
        boolean runtimeChanged = !newFingerprint.equals(existing.getTestFingerprint());
        if (runtimeChanged) {
            config.setTestStatus(0);
            config.setTestFingerprint(null);
            config.setLastTestedAt(null);
            config.setLastTestMessage("配置已修改，请重新测试");
            config.setIsDefault(0);
        } else {
            config.setTestStatus(existing.getTestStatus());
            config.setTestFingerprint(existing.getTestFingerprint());
            config.setLastTestedAt(existing.getLastTestedAt());
            config.setLastTestMessage(existing.getLastTestMessage());
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

        if (!Integer.valueOf(1).equals(config.getStatus()) || !Integer.valueOf(1).equals(config.getTestStatus())
                || !fingerprint(config, secretCipher.decrypt(config.getApiKey())).equals(config.getTestFingerprint())) {
            throw new BusinessException(ErrorCode.AI_CONFIG_NOT_TESTED.getCode(),
                    "配置必须启用且使用当前参数测试成功后才能设为默认");
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

    @Override
    public String testConnection(Long id, MultipartFile audioSample) {
        AiConfig config = aiConfigMapper.selectById(id);
        if (config == null) {
            throw new BusinessException(ErrorCode.AI_CONFIG_NOT_FOUND.getCode(), ErrorCode.AI_CONFIG_NOT_FOUND.getMessage());
        }
        validate(config);
        try {
            String message;
            if (Integer.valueOf(ConfigType.ASR.getCode()).equals(config.getConfigType())) {
                FileUtil.validateAudioFile(audioSample);
                if (audioSample.getSize() > 20L * 1024 * 1024) {
                    throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(), "配置测试音频不能超过20MB");
                }
                String suffix = audioSample.getOriginalFilename().toLowerCase().endsWith(".mp3") ? ".mp3" : ".wav";
                Path temp = Files.createTempFile("funasr-config-test-", suffix);
                try {
                    audioSample.transferTo(temp);
                    AsrClient client = clientFactory.getAsrClientByConfigId(id);
                    String transcript = client instanceof WholeFileAsrClient whole
                            ? whole.transcribeDetailed(temp, "zh").text()
                            : client.transcribe(audioSample, "zh");
                    if (transcript == null || transcript.isBlank()) {
                        throw new IllegalStateException("模型返回空转写");
                    }
                    message = "FunASR配置测试成功";
                } finally {
                    Files.deleteIfExists(temp);
                }
            } else {
                LlmClient client = clientFactory.getLlmClientByConfigId(id);
                String response = client.call("只回复OK", "这是连接测试。", id);
                if (response == null || response.isBlank()) throw new IllegalStateException("模型返回空响应");
                message = "文本模型配置测试成功";
            }
            config.setTestStatus(1);
            config.setTestFingerprint(fingerprint(config, secretCipher.decrypt(config.getApiKey())));
            config.setLastTestedAt(LocalDateTime.now());
            config.setLastTestMessage(message);
            aiConfigMapper.updateById(config);
            return message;
        } catch (BusinessException e) {
            markTestFailed(config, e.getMessage());
            throw e;
        } catch (Exception e) {
            markTestFailed(config, e.getMessage());
            throw new BusinessException(ErrorCode.AI_MODEL_CALL_FAILED.getCode(), "AI配置测试失败: " + e.getMessage());
        }
    }

    private void markTestFailed(AiConfig config, String message) {
        config.setTestStatus(2);
        config.setTestFingerprint(null);
        config.setLastTestedAt(LocalDateTime.now());
        config.setLastTestMessage(message == null ? "测试失败" : message.substring(0, Math.min(500, message.length())));
        config.setIsDefault(0);
        aiConfigMapper.updateById(config);
    }

    private void validate(AiConfig config) {
        if (config.getConfigType() == null || config.getProvider() == null || config.getProvider().isBlank()
                || config.getModelName() == null || config.getModelName().isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(), "配置类型、供应商和模型名称不能为空");
        }
        String provider = config.getProvider().trim().toLowerCase();
        if (config.getConfigType() == ConfigType.ASR.getCode() && !"funasr".equals(provider)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(), "ASR仅支持funasr");
        }
        if (config.getConfigType() == ConfigType.LLM.getCode()
                && !java.util.Set.of("deepseek", "xiaomi").contains(provider)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(), "文本模型仅支持deepseek或xiaomi");
        }
        try {
            URI endpoint = URI.create(config.getApiEndpoint());
            if (!java.util.Set.of("http", "https").contains(endpoint.getScheme()) || endpoint.getHost() == null) {
                throw new IllegalArgumentException();
            }
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(), "API端点必须是有效的HTTP(S)地址");
        }
    }

    private String fingerprint(AiConfig config, String plainKey) {
        try {
            String source = config.getProvider() + "\n" + config.getModelName() + "\n"
                    + config.getApiEndpoint() + "\n" + config.getConfigType() + "\n" + (plainKey == null ? "" : plainKey);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(source.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("生成配置指纹失败", e);
        }
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
