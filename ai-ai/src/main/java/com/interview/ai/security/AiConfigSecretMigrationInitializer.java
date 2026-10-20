package com.interview.ai.security;

import com.interview.ai.entity.AiConfig;
import com.interview.ai.mapper.AiConfigMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** 启动时将历史明文 AI API Key 一次性迁移为 AES-GCM 密文。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiConfigSecretMigrationInitializer implements ApplicationRunner {

    private final AiConfigMapper aiConfigMapper;
    private final AiConfigSecretCipher secretCipher;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        List<AiConfig> configs = aiConfigMapper.selectList(null);
        int migrated = 0;
        for (AiConfig config : configs) {
            String apiKey = config.getApiKey();
            if (apiKey == null || apiKey.isBlank()) {
                continue;
            }
            if (secretCipher.isEncrypted(apiKey)) {
                // 启动时验证现有密文，防止错误主密钥直到业务调用时才暴露。
                secretCipher.decrypt(apiKey);
                continue;
            }
            AiConfig update = new AiConfig();
            update.setId(config.getId());
            update.setApiKey(secretCipher.encrypt(apiKey));
            aiConfigMapper.updateById(update);
            migrated++;
        }
        if (migrated > 0) {
            log.info("已完成历史 AI API Key 加密迁移，数量={}", migrated);
        }
    }
}
