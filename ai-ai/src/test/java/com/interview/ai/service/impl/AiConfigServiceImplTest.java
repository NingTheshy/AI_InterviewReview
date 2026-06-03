package com.interview.ai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.interview.ai.entity.AiConfig;
import com.interview.ai.mapper.AiConfigMapper;
import com.interview.common.constant.ConfigType;
import com.interview.common.exception.BusinessException;
import com.interview.common.result.PageResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AiConfigServiceImpl 测试")
class AiConfigServiceImplTest {

    @Mock
    private AiConfigMapper aiConfigMapper;

    @InjectMocks
    private AiConfigServiceImpl aiConfigService;

    private AiConfig mockConfig;

    @BeforeEach
    void setUp() {
        mockConfig = new AiConfig();
        mockConfig.setId(1L);
        mockConfig.setConfigName("DeepSeek");
        mockConfig.setProvider("deepseek");
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
        aiConfigService.create(mockConfig);
        verify(aiConfigMapper).insert(mockConfig);
    }

    @Test
    @DisplayName("create - 设为默认时先取消其他默认")
    void create_setDefault_clearsExistingDefault() {
        mockConfig.setIsDefault(1);
        AiConfig existingDefault = new AiConfig();
        existingDefault.setId(2L);
        existingDefault.setIsDefault(1);
        when(aiConfigMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(existingDefault);

        aiConfigService.create(mockConfig);

        verify(aiConfigMapper).updateById(existingDefault);
        verify(aiConfigMapper).insert(mockConfig);
    }

    @Test
    @DisplayName("update - 存在时更新成功")
    void update_exists_updatesConfig() {
        when(aiConfigMapper.selectById(1L)).thenReturn(mockConfig);
        aiConfigService.update(1L, mockConfig);
        verify(aiConfigMapper).updateById(mockConfig);
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
        Page<AiConfig> page = new Page<>(1, 10);
        page.setRecords(List.of(mockConfig));
        page.setTotal(1);
        when(aiConfigMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(page);

        PageResult<AiConfig> result = aiConfigService.list(1, 10);
        assertNotNull(result);
        assertEquals(1, result.getRecords().size());
        assertEquals(1, result.getTotal());
    }
}
