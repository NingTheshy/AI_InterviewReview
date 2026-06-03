package com.interview.interview.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.interview.common.exception.BusinessException;
import com.interview.common.exception.GlobalExceptionHandler;
import com.interview.common.result.PageResult;
import com.interview.interview.dto.ExperienceDetailResponse;
import com.interview.interview.dto.ExperienceListResponse;
import com.interview.interview.service.ExperienceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * ExperienceController 功能测试
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ExperienceController 功能测试")
class ExperienceControllerTest {

    private MockMvc mockMvc;
    private ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private ExperienceService experienceService;

    @InjectMocks
    private ExperienceController experienceController;

    private static final String TEST_TOKEN = "abc123def456";

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(experienceController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    // ==================== list 测试 ====================

    @Nested
    @DisplayName("GET /experiences 测试")
    class ListTest {

        @Test
        @DisplayName("获取面经列表成功")
        void list_Success() throws Exception {
            ExperienceListResponse item = ExperienceListResponse.builder()
                    .token(TEST_TOKEN)
                    .title("Java 面试")
                    .companyName("阿里巴巴")
                    .build();
            PageResult<ExperienceListResponse> pageResult = new PageResult<>(
                    List.of(item), 1, 1, 10);
            when(experienceService.listPublic(anyInt(), anyInt(),
                    any(), any(), any(), any())).thenReturn(pageResult);

            mockMvc.perform(get("/experiences")
                            .param("page", "1")
                            .param("size", "10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.total").value(1))
                    .andExpect(jsonPath("$.data.records[0].title").value("Java 面试"));
        }

        @Test
        @DisplayName("获取面经列表 - 带筛选条件")
        void list_WithFilters() throws Exception {
            PageResult<ExperienceListResponse> pageResult = new PageResult<>(List.of(), 0, 1, 10);
            when(experienceService.listPublic(anyInt(), anyInt(),
                    any(), any(), any(), any())).thenReturn(pageResult);

            mockMvc.perform(get("/experiences")
                            .param("companyName", "阿里巴巴")
                            .param("sortBy", "viewCount"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.total").value(0));
        }
    }

    // ==================== getDetail 测试 ====================

    @Nested
    @DisplayName("GET /experiences/{token} 测试")
    class GetDetailTest {

        @Test
        @DisplayName("获取面经详情成功")
        void getDetail_Success() throws Exception {
            ExperienceDetailResponse detail = ExperienceDetailResponse.builder()
                    .token(TEST_TOKEN)
                    .title("Java 面试")
                    .companyName("阿里巴巴")
                    .build();
            when(experienceService.getDetail(anyString())).thenReturn(detail);

            mockMvc.perform(get("/experiences/{token}", TEST_TOKEN))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.title").value("Java 面试"));
        }

        @Test
        @DisplayName("获取面经详情 - 面经不存在")
        void getDetail_NotFound() throws Exception {
            when(experienceService.getDetail(anyString()))
                    .thenThrow(new BusinessException(5003, "面经已下架或不存在"));

            mockMvc.perform(get("/experiences/{token}", TEST_TOKEN))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value(5003));
        }
    }
}
