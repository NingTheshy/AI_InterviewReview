package com.interview.interview.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.interview.common.exception.BusinessException;
import com.interview.common.exception.GlobalExceptionHandler;
import com.interview.common.result.PageResult;
import com.interview.interview.dto.CommentRequest;
import com.interview.interview.dto.CommentResponse;
import com.interview.interview.service.CommentService;
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

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * CommentController 功能测试
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CommentController 功能测试")
class CommentControllerTest {

    private MockMvc mockMvc;
    private ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private CommentService commentService;

    @InjectMocks
    private CommentController commentController;

    private static final Long TEST_USER_ID = 1L;
    private static final String TEST_TOKEN = "abc123def456";
    private static final Long TEST_COMMENT_ID = 20L;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(commentController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(TEST_USER_ID, "testuser",
                        List.of(new SimpleGrantedAuthority("ROLE_USER"))));
    }

    // ==================== listComments 测试 ====================

    @Nested
    @DisplayName("GET /experiences/{token}/comments 测试")
    class ListCommentsTest {

        @Test
        @DisplayName("获取评论列表成功")
        void listComments_Success() throws Exception {
            CommentResponse comment = CommentResponse.builder()
                    .id(1L)
                    .userId(TEST_USER_ID)
                    .nickname("测试用户")
                    .content("很好的面经！")
                    .build();
            PageResult<CommentResponse> pageResult = new PageResult<>(
                    List.of(comment), 1, 1, 20);
            when(commentService.listByShareToken(anyString(), anyInt(), anyInt())).thenReturn(pageResult);

            mockMvc.perform(get("/experiences/{token}/comments", TEST_TOKEN)
                            .param("page", "1")
                            .param("size", "20"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.total").value(1));
        }
    }

    // ==================== addComment 测试 ====================

    @Nested
    @DisplayName("POST /experiences/{token}/comments 测试")
    class AddCommentTest {

        @Test
        @DisplayName("发表评论成功")
        void addComment_Success() throws Exception {
            CommentRequest request = new CommentRequest();
            request.setContent("很好的面经！");
            doNothing().when(commentService).addComment(anyString(), anyLong(), anyString());

            mockMvc.perform(post("/experiences/{token}/comments", TEST_TOKEN)
                            .contentType("application/json")
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));
        }

        @Test
        @DisplayName("发表评论 - 内容为空")
        void addComment_Empty() throws Exception {
            CommentRequest request = new CommentRequest();
            request.setContent("");

            mockMvc.perform(post("/experiences/{token}/comments", TEST_TOKEN)
                            .contentType("application/json")
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }
    }

    // ==================== deleteComment 测试 ====================

    @Nested
    @DisplayName("DELETE /comments/{id} 测试")
    class DeleteCommentTest {

        @Test
        @DisplayName("删除评论成功")
        void deleteComment_Success() throws Exception {
            doNothing().when(commentService).deleteComment(anyLong(), anyLong());

            mockMvc.perform(delete("/comments/{id}", TEST_COMMENT_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));
        }

        @Test
        @DisplayName("删除评论 - 无权限")
        void deleteComment_AccessDenied() throws Exception {
            doThrow(new BusinessException(5006, "无权删除该评论"))
                    .when(commentService).deleteComment(anyLong(), anyLong());

            mockMvc.perform(delete("/comments/{id}", TEST_COMMENT_ID))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value(5006));
        }
    }
}
