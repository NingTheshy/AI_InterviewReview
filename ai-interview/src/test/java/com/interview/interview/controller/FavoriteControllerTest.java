package com.interview.interview.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.interview.common.exception.BusinessException;
import com.interview.common.exception.GlobalExceptionHandler;
import com.interview.common.result.PageResult;
import com.interview.interview.dto.FavoriteRequest;
import com.interview.interview.dto.FavoriteResponse;
import com.interview.interview.service.FavoriteService;
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
 * FavoriteController 功能测试
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("FavoriteController 功能测试")
class FavoriteControllerTest {

    private MockMvc mockMvc;
    private ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private FavoriteService favoriteService;

    @InjectMocks
    private FavoriteController favoriteController;

    private static final Long TEST_USER_ID = 1L;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(favoriteController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(TEST_USER_ID, "testuser",
                        List.of(new SimpleGrantedAuthority("ROLE_USER"))));
    }

    // ==================== addFavorite 测试 ====================

    @Nested
    @DisplayName("POST /favorites 测试")
    class AddFavoriteTest {

        @Test
        @DisplayName("收藏成功")
        void addFavorite_Success() throws Exception {
            FavoriteRequest request = new FavoriteRequest();
            request.setInterviewId(100L);
            request.setQuestionId(10L);
            request.setRemark("好问题");
            doNothing().when(favoriteService).addFavorite(anyLong(), anyLong(), anyLong(), any());

            mockMvc.perform(post("/favorites")
                            .contentType("application/json")
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));
        }

        @Test
        @DisplayName("收藏 - 已收藏")
        void addFavorite_AlreadyExists() throws Exception {
            FavoriteRequest request = new FavoriteRequest();
            request.setInterviewId(100L);
            doThrow(new BusinessException(4002, "该问题已收藏"))
                    .when(favoriteService).addFavorite(anyLong(), anyLong(), any(), any());

            mockMvc.perform(post("/favorites")
                            .contentType("application/json")
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value(4002));
        }
    }

    // ==================== removeFavorite 测试 ====================

    @Nested
    @DisplayName("DELETE /favorites/{id} 测试")
    class RemoveFavoriteTest {

        @Test
        @DisplayName("取消收藏成功")
        void removeFavorite_Success() throws Exception {
            doNothing().when(favoriteService).removeFavorite(anyLong(), anyLong());

            mockMvc.perform(delete("/favorites/{id}", 1L))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));
        }
    }

    // ==================== list 测试 ====================

    @Nested
    @DisplayName("GET /favorites 测试")
    class ListTest {

        @Test
        @DisplayName("获取收藏列表成功")
        void list_Success() throws Exception {
            FavoriteResponse favorite = FavoriteResponse.builder()
                    .id(1L)
                    .interviewId(100L)
                    .questionId(10L)
                    .remark("好问题")
                    .createdAt(java.time.LocalDateTime.now())
                    .build();
            PageResult<FavoriteResponse> pageResult = new PageResult<>(
                    List.of(favorite), 1, 1, 10);
            when(favoriteService.list(anyLong(), anyInt(), anyInt())).thenReturn(pageResult);

            mockMvc.perform(get("/favorites")
                            .param("page", "1")
                            .param("size", "10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.total").value(1));
        }

        @Test
        @DisplayName("获取收藏列表 - 空列表")
        void list_Empty() throws Exception {
            PageResult<FavoriteResponse> pageResult = new PageResult<>(
                    List.of(), 0, 1, 10);
            when(favoriteService.list(anyLong(), anyInt(), anyInt())).thenReturn(pageResult);

            mockMvc.perform(get("/favorites")
                            .param("page", "1")
                            .param("size", "10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.total").value(0));
        }

        @Test
        @DisplayName("获取收藏列表 - 使用默认分页参数")
        void list_DefaultParams() throws Exception {
            PageResult<FavoriteResponse> pageResult = new PageResult<>(
                    List.of(), 0, 1, 10);
            when(favoriteService.list(anyLong(), anyInt(), anyInt())).thenReturn(pageResult);

            mockMvc.perform(get("/favorites"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));
        }
    }

    // ==================== 边界条件测试 ====================

    @Nested
    @DisplayName("边界条件测试")
    class BoundaryTest {

        @Test
        @DisplayName("收藏 - 缺少面试 ID")
        void addFavorite_MissingInterviewId() throws Exception {
            mockMvc.perform(post("/favorites")
                            .contentType("application/json")
                            .content("{}"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("收藏 - 备注超长")
        void addFavorite_RemarkTooLong() throws Exception {
            FavoriteRequest request = new FavoriteRequest();
            request.setInterviewId(100L);
            request.setRemark("a".repeat(201));

            mockMvc.perform(post("/favorites")
                            .contentType("application/json")
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("收藏 - 达到上限")
        void addFavorite_LimitReached() throws Exception {
            FavoriteRequest request = new FavoriteRequest();
            request.setInterviewId(100L);
            doThrow(new BusinessException(4001, "收藏数已达上限"))
                    .when(favoriteService).addFavorite(anyLong(), anyLong(), any(), any());

            mockMvc.perform(post("/favorites")
                            .contentType("application/json")
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(4001));
        }

        @Test
        @DisplayName("取消收藏 - 收藏不存在")
        void removeFavorite_NotFound() throws Exception {
            doThrow(new BusinessException(1004, "记录不存在"))
                    .when(favoriteService).removeFavorite(anyLong(), anyLong());

            mockMvc.perform(delete("/favorites/{id}", 999L))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value(1004));
        }
    }
}
