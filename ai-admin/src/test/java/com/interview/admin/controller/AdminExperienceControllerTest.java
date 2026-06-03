package com.interview.admin.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.interview.admin.dto.ExperienceManagementResponse;
import com.interview.admin.dto.ExperienceStatusRequest;
import com.interview.admin.service.AdminService;
import com.interview.common.exception.BusinessException;
import com.interview.common.exception.GlobalExceptionHandler;
import com.interview.common.result.PageResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * AdminExperienceController 功能测试
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AdminExperienceController 功能测试")
class AdminExperienceControllerTest {

    private MockMvc mockMvc;
    private ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private AdminService adminService;

    @InjectMocks
    private AdminExperienceController adminExperienceController;

    private static final Long TEST_USER_ID = 1L;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(adminExperienceController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(TEST_USER_ID, "admin",
                        List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
    }

    // ==================== 获取面经列表测试 ====================

    @Nested
    @DisplayName("GET /admin/experiences 测试")
    class ListExperiencesTest {

        @Test
        @DisplayName("获取面经列表成功")
        void listExperiences_Success() throws Exception {
            ExperienceManagementResponse experience = ExperienceManagementResponse.builder()
                    .id(1L)
                    .interviewId(100L)
                    .userId(TEST_USER_ID)
                    .username("testuser")
                    .shareToken("testtoken123")
                    .isPublic(1)
                    .viewCount(100)
                    .createdAt(LocalDateTime.now())
                    .build();
            when(adminService.listExperiences(anyInt(), anyInt()))
                    .thenReturn(new PageResult<>(List.of(experience), 1, 1, 10));

            mockMvc.perform(get("/admin/experiences")
                            .param("page", "1")
                            .param("size", "10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.records.length()").value(1));
        }

        @Test
        @DisplayName("获取面经列表 - 空列表")
        void listExperiences_Empty() throws Exception {
            when(adminService.listExperiences(anyInt(), anyInt()))
                    .thenReturn(new PageResult<>(List.of(), 0, 1, 10));

            mockMvc.perform(get("/admin/experiences")
                            .param("page", "1")
                            .param("size", "10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.records.length()").value(0));
        }
    }

    // ==================== 切换面经状态测试 ====================

    @Nested
    @DisplayName("PUT /admin/experiences/{token}/status 测试")
    class SetExperienceStatusTest {

        @Test
        @DisplayName("设置面经状态成功")
        void setExperienceStatus_Success() throws Exception {
            doNothing().when(adminService).setExperienceStatus(anyString(), anyInt());

            ExperienceStatusRequest request = new ExperienceStatusRequest();
            request.setIsPublic(0);

            mockMvc.perform(put("/admin/experiences/{token}/status", "testtoken123")
                            .contentType("application/json")
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));
        }

        @Test
        @DisplayName("设置面经状态 - 分享不存在")
        void setExperienceStatus_NotFound() throws Exception {
            doThrow(new BusinessException(4004, "分享链接不存在"))
                    .when(adminService).setExperienceStatus(eq("nonexistent"), anyInt());

            ExperienceStatusRequest request = new ExperienceStatusRequest();
            request.setIsPublic(0);

            mockMvc.perform(put("/admin/experiences/{token}/status", "nonexistent")
                            .contentType("application/json")
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isGone())
                    .andExpect(jsonPath("$.code").value(4004));
        }
    }
}
