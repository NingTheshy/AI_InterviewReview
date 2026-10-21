package com.interview.ai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.interview.ai.entity.AiConfig;
import com.interview.ai.factory.AiClientFactory;
import com.interview.ai.mapper.AiConfigMapper;
import com.interview.ai.security.AiConfigSecretCipher;
import com.interview.auth.service.AuditService;
import com.interview.common.constant.ConfigType;
import com.interview.common.exception.BusinessException;
import com.interview.common.result.PageResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Base64;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AiConfigServiceImpl 测试")
class AiConfigServiceImplTest {

    @Mock
    private AiConfigMapper aiConfigMapper;

    @Mock
    private AuditService auditService;

    @Mock
    private AiClientFactory aiClientFactory;

    private AiConfigServiceImpl aiConfigService;

    private AiConfigSecretCipher secretCipher;

    private AiConfig mockConfig;

    @BeforeEach
    void setUp() {
        secretCipher = new AiConfigSecretCipher(Base64.getEncoder()
                .encodeToString("0123456789abcdef0123456789abcdef".getBytes()));
        aiConfigService = new AiConfigServiceImpl(aiConfigMapper, auditService, secretCipher, aiClientFactory);
        mockConfig = new AiConfig();
        mockConfig.setId(1L);
        mockConfig.setConfigName("DeepSeek");
        mockConfig.setProvider("deepseek");
        mockConfig.setModelName("deepseek-chat");
        mockConfig.setApiEndpoint("https://api.deepseek.com/v1");
        mockConfig.setConfigType(ConfigType.LLM.getCode());
        mockConfig.setIsDefault(0);
        mockConfig.setStatus(1);
    }

    @Test
    @DisplayName("getDetail - 存在时返回配置")
    void getDetail_exists_returnsConfig() {
        when(aiConfigMapper.selectById(1L)).thenReturn(mockConfig);
        AiConfig result = aiConfigService.getDetail(1L);
        assertNotNull(result);
        assertEquals("DeepSeek", result.getConfigName());
    }

    @Test
    @DisplayName("getDetail - 不存在时抛出异常")
    void getDetail_notExists_throwsException() {
        when(aiConfigMapper.selectById(999L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> aiConfigService.getDetail(999L));
    }

    @Test
    @DisplayName("create - 创建配置成功")
    void create_validConfig_inserts() {
        mockConfig.setApiKey("sk-create-secret");

        aiConfigService.create(mockConfig);

        verify(aiConfigMapper).insert(mockConfig);
        assertTrue(secretCipher.isEncrypted(mockConfig.getApiKey()));
        assertEquals("sk-create-secret", secretCipher.decrypt(mockConfig.getApiKey()));
    }

    @Test
    @DisplayName("create - 未测试的新配置不能直接设为默认")
    void create_setDefault_rejectedBeforeConnectionTest() {
        mockConfig.setIsDefault(1);

        assertThrows(BusinessException.class, () -> aiConfigService.create(mockConfig));
        verify(aiConfigMapper, never()).insert(any());
    }

    @Test
    @DisplayName("update - 存在时更新成功")
    void update_exists_updatesConfig() {
        when(aiConfigMapper.selectById(1L)).thenReturn(mockConfig);
        aiConfigService.update(1L, mockConfig);
        verify(aiConfigMapper).updateById(mockConfig);
    }

    @Test
    @DisplayName("update - 提交脱敏值时保留原 API Key")
    void update_maskedKey_preservesExistingSecret() {
        AiConfig existing = new AiConfig();
        existing.setId(1L);
        existing.setApiKey(secretCipher.encrypt("sk-old-secret"));
        when(aiConfigMapper.selectById(1L)).thenReturn(existing);

        AiConfig request = new AiConfig();
        request.setConfigName("DeepSeek");
        request.setProvider("deepseek");
        request.setModelName("deepseek-chat");
        request.setApiEndpoint("https://api.deepseek.com/v1");
        request.setConfigType(ConfigType.LLM.getCode());
        request.setApiKey("****cret");
        aiConfigService.update(1L, request);

        assertTrue(secretCipher.isEncrypted(request.getApiKey()));
        assertEquals("sk-old-secret", secretCipher.decrypt(request.getApiKey()));
        verify(aiConfigMapper).updateById(request);
    }

    @Test
    @DisplayName("update - 不存在时抛出异常")
    void update_notExists_throwsException() {
        when(aiConfigMapper.selectById(999L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> aiConfigService.update(999L, mockConfig));
    }

    @Test
    @DisplayName("delete - 存在时删除成功")
    void delete_exists_deletesConfig() {
        when(aiConfigMapper.selectById(1L)).thenReturn(mockConfig);
        aiConfigService.delete(1L);
        verify(aiConfigMapper).deleteById(1L);
    }

    @Test
    @DisplayName("delete - 不存在时抛出异常")
    void delete_notExists_throwsException() {
        when(aiConfigMapper.selectById(999L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> aiConfigService.delete(999L));
    }

    @Test
    @DisplayName("setDefault - 设置默认配置成功")
    void setDefault_exists_setsDefault() {
        String plainKey = "sk-tested-secret";
        mockConfig.setApiKey(secretCipher.encrypt(plainKey));
        mockConfig.setTestStatus(1);
        mockConfig.setTestFingerprint(fingerprint(mockConfig, plainKey));
        when(aiConfigMapper.selectById(1L)).thenReturn(mockConfig);
        aiConfigService.setDefault(1L);
        assertEquals(1, mockConfig.getIsDefault());
        verify(aiConfigMapper).updateById(mockConfig);
    }

    @Test
    @DisplayName("setDefault - 不存在时抛出异常")
    void setDefault_notExists_throwsException() {
        when(aiConfigMapper.selectById(999L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> aiConfigService.setDefault(999L));
    }

    @Test
    @DisplayName("list - 返回分页结果")
    void list_validParams_returnsPageResult() {
        mockConfig.setApiKey(secretCipher.encrypt("sk-list-secret"));
        Page<AiConfig> page = new Page<>(1, 10);
        page.setRecords(List.of(mockConfig));
        page.setTotal(1);
        when(aiConfigMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(page);

        PageResult<AiConfig> result = aiConfigService.list(1, 10);
        assertNotNull(result);
        assertEquals(1, result.getRecords().size());
        assertEquals(1, result.getTotal());
        assertEquals("sk-list-secret", result.getRecords().get(0).getApiKey());
    }

    private String fingerprint(AiConfig config, String plainKey) {
        try {
            String source = config.getProvider() + "\n" + config.getModelName() + "\n"
                    + config.getApiEndpoint() + "\n" + config.getConfigType() + "\n" + plainKey;
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(source.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
