package com.interview.ai.security;

import com.interview.ai.entity.AiConfig;
import com.interview.ai.mapper.AiConfigMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 历史明文密钥启动迁移测试。 */
@ExtendWith(MockitoExtension.class)
class AiConfigSecretMigrationInitializerTest {

    private static final String KEY = Base64.getEncoder().encodeToString(
            "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8));

    @Mock private AiConfigMapper aiConfigMapper;

    @Test
    @DisplayName("启动时只加密历史明文 API Key")
    void plaintextSecretIsMigrated() {
        AiConfigSecretCipher cipher = new AiConfigSecretCipher(KEY);
        AiConfig plaintext = config(1L, "sk-legacy-secret");
        AiConfig encrypted = config(2L, cipher.encrypt("sk-already-encrypted"));
        AiConfig blank = config(3L, "");
        when(aiConfigMapper.selectList(null)).thenReturn(List.of(plaintext, encrypted, blank));
        AiConfigSecretMigrationInitializer initializer =
                new AiConfigSecretMigrationInitializer(aiConfigMapper, cipher);

        initializer.run(null);

        ArgumentCaptor<AiConfig> captor = ArgumentCaptor.forClass(AiConfig.class);
        verify(aiConfigMapper).updateById(captor.capture());
        AiConfig update = captor.getValue();
        assertEquals(1L, update.getId());
        assertTrue(cipher.isEncrypted(update.getApiKey()));
        assertEquals("sk-legacy-secret", cipher.decrypt(update.getApiKey()));
    }

    @Test
    @DisplayName("错误主密钥在启动迁移阶段立即失败")
    void wrongMasterKeyFailsFast() {
        AiConfigSecretCipher original = new AiConfigSecretCipher(KEY);
        String otherKey = Base64.getEncoder().encodeToString(
                "abcdef0123456789abcdef0123456789".getBytes(StandardCharsets.UTF_8));
        AiConfigSecretCipher wrong = new AiConfigSecretCipher(otherKey);
        when(aiConfigMapper.selectList(null))
                .thenReturn(List.of(config(1L, original.encrypt("sk-existing"))));
        AiConfigSecretMigrationInitializer initializer =
                new AiConfigSecretMigrationInitializer(aiConfigMapper, wrong);

        assertThrows(IllegalStateException.class, () -> initializer.run(null));
        verify(aiConfigMapper, never()).updateById(org.mockito.ArgumentMatchers.any());
    }

    private AiConfig config(Long id, String apiKey) {
        AiConfig config = new AiConfig();
        config.setId(id);
        config.setApiKey(apiKey);
        return config;
    }
}
