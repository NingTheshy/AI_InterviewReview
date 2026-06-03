package com.interview.admin.controller;

import com.interview.admin.dto.InterviewManagementResponse;
import com.interview.admin.service.AdminService;
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
 * AdminInterviewController 功能测试
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AdminInterviewController 功能测试")
class AdminInterviewControllerTest {

    private MockMvc mockMvc;

    @Mock
    private AdminService adminService;

    @InjectMocks
    private AdminInterviewController adminInterviewController;

    private static final Long TEST_USER_ID = 1L;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(adminInterviewController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(TEST_USER_ID, "admin",
                        List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
    }

    // ==================== 获取面试列表测试 ====================

    @Nested
    @DisplayName("GET /admin/interviews 测试")
    class ListInterviewsTest {

        @Test
        @DisplayName("获取面试列表成功")
        void listInterviews_Success() throws Exception {
            InterviewManagementResponse interview = InterviewManagementResponse.builder()
                    .id(100L)
                    .userId(TEST_USER_ID)
                    .username("testuser")
                    .title("Java后端面试")
                    .companyName("阿里巴巴")
                    .status(3)
                    .overallScore(85)
                    .createdAt(LocalDateTime.now())
                    .build();
            when(adminService.listInterviews(anyInt(), anyInt(), any(), any()))
                    .thenReturn(new PageResult<>(List.of(interview), 1, 1, 10));

            mockMvc.perform(get("/admin/interviews")
                            .param("page", "1")
                            .param("size", "10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.records.length()").value(1));
        }

        @Test
        @DisplayName("获取面试列表 - 空列表")
        void listInterviews_Empty() throws Exception {
            when(adminService.listInterviews(anyInt(), anyInt(), any(), any()))
                    .thenReturn(new PageResult<>(List.of(), 0, 1, 10));

            mockMvc.perform(get("/admin/interviews")
                            .param("page", "1")
                            .param("size", "10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.records.length()").value(0));
        }

        @Test
        @DisplayName("获取面试列表 - 按用户筛选")
        void listInterviews_ByUserId() throws Exception {
            when(adminService.listInterviews(anyInt(), anyInt(), anyLong(), any()))
                    .thenReturn(new PageResult<>(List.of(), 0, 1, 10));

            mockMvc.perform(get("/admin/interviews")
                            .param("page", "1")
                            .param("size", "10")
                            .param("userId", "1"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("获取面试列表 - 按状态筛选")
        void listInterviews_ByStatus() throws Exception {
            when(adminService.listInterviews(anyInt(), anyInt(), any(), anyInt()))
                    .thenReturn(new PageResult<>(List.of(), 0, 1, 10));

            mockMvc.perform(get("/admin/interviews")
                            .param("page", "1")
                            .param("size", "10")
                            .param("status", "3"))
                    .andExpect(status().isOk());
        }
    }
}
