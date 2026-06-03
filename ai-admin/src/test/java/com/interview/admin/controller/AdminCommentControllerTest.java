package com.interview.admin.controller;

import com.interview.admin.dto.CommentManagementResponse;
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
 * AdminCommentController 功能测试
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AdminCommentController 功能测试")
class AdminCommentControllerTest {

    private MockMvc mockMvc;

    @Mock
    private AdminService adminService;

    @InjectMocks
    private AdminCommentController adminCommentController;

    private static final Long TEST_USER_ID = 1L;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(adminCommentController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(TEST_USER_ID, "admin",
                        List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
    }

    // ==================== 获取评论列表测试 ====================

    @Nested
    @DisplayName("GET /admin/comments 测试")
    class ListCommentsTest {

        @Test
        @DisplayName("获取评论列表成功")
        void listComments_Success() throws Exception {
            CommentManagementResponse comment = CommentManagementResponse.builder()
                    .id(1L)
                    .shareId(10L)
                    .userId(TEST_USER_ID)
                    .username("testuser")
                    .content("很好的面试分享！")
                    .createdAt(LocalDateTime.now())
                    .build();
            when(adminService.listComments(anyInt(), anyInt(), any()))
                    .thenReturn(new PageResult<>(List.of(comment), 1, 1, 10));

            mockMvc.perform(get("/admin/comments")
                            .param("page", "1")
                            .param("size", "10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.records.length()").value(1));
        }

        @Test
        @DisplayName("获取评论列表 - 空列表")
        void listComments_Empty() throws Exception {
            when(adminService.listComments(anyInt(), anyInt(), any()))
                    .thenReturn(new PageResult<>(List.of(), 0, 1, 10));

            mockMvc.perform(get("/admin/comments")
                            .param("page", "1")
                            .param("size", "10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.records.length()").value(0));
        }

        @Test
        @DisplayName("获取评论列表 - 按用户筛选")
        void listComments_ByUserId() throws Exception {
            when(adminService.listComments(anyInt(), anyInt(), anyLong()))
                    .thenReturn(new PageResult<>(List.of(), 0, 1, 10));

            mockMvc.perform(get("/admin/comments")
                            .param("page", "1")
                            .param("size", "10")
                            .param("userId", "1"))
                    .andExpect(status().isOk());
        }
    }

    // ==================== 删除评论测试 ====================

    @Nested
    @DisplayName("DELETE /admin/comments/{id} 测试")
    class DeleteCommentTest {

        @Test
        @DisplayName("删除评论成功")
        void deleteComment_Success() throws Exception {
            doNothing().when(adminService).deleteComment(anyLong());

            mockMvc.perform(delete("/admin/comments/{id}", 1L))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));
        }

        @Test
        @DisplayName("删除评论 - 评论不存在")
        void deleteComment_NotFound() throws Exception {
            doThrow(new BusinessException(404, "资源不存在"))
                    .when(adminService).deleteComment(eq(999L));

            mockMvc.perform(delete("/admin/comments/{id}", 999L))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value(404));
        }
    }
}
