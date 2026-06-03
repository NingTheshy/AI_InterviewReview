package com.interview.interview.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.interview.common.exception.BusinessException;
import com.interview.common.exception.GlobalExceptionHandler;
import com.interview.interview.dto.ExperienceDetailResponse;
import com.interview.interview.dto.ShareRequest;
import com.interview.interview.dto.ShareResponse;
import com.interview.interview.service.ShareService;
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
 * ShareController 功能测试
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ShareController 功能测试")
class ShareControllerTest {

    private MockMvc mockMvc;
    private ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private ShareService shareService;

    @InjectMocks
    private ShareController shareController;

    private static final Long TEST_USER_ID = 1L;
    private static final Long TEST_INTERVIEW_ID = 100L;
    private static final String TEST_TOKEN = "abc123def456";

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(shareController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(TEST_USER_ID, "testuser",
                        List.of(new SimpleGrantedAuthority("ROLE_USER"))));
    }

    // ==================== createShare 测试 ====================

    @Nested
    @DisplayName("POST /interviews/{id}/share 测试")
    class CreateShareTest {

        @Test
        @DisplayName("创建分享成功")
        void createShare_Success() throws Exception {
            ShareResponse share = ShareResponse.builder()
                    .id(1L)
                    .interviewId(TEST_INTERVIEW_ID)
                    .token(TEST_TOKEN)
                    .isPublic(0)
                    .viewCount(0)
                    .createdAt(java.time.LocalDateTime.now())
                    .build();
            when(shareService.createShare(anyLong(), anyLong(), any(), any())).thenReturn(share);

            ShareRequest request = new ShareRequest();
            request.setExpireType("7d");
            request.setIsPublic(false);

            mockMvc.perform(post("/interviews/{id}/share", TEST_INTERVIEW_ID)
                            .contentType("application/json")
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.token").value(TEST_TOKEN));
        }
    }

    // ==================== listShares 测试 ====================

    @Nested
    @DisplayName("GET /interviews/{id}/shares 测试")
    class ListSharesTest {

        @Test
        @DisplayName("获取分享列表成功")
        void listShares_Success() throws Exception {
            ShareResponse share = ShareResponse.builder()
                    .id(1L)
                    .token(TEST_TOKEN)
                    .build();
            when(shareService.listShares(anyLong(), anyLong())).thenReturn(List.of(share));

            mockMvc.perform(get("/interviews/{id}/shares", TEST_INTERVIEW_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.length()").value(1));
        }
    }

    // ==================== deleteShare 测试 ====================

    @Nested
    @DisplayName("DELETE /shares/{token} 测试")
    class DeleteShareTest {

        @Test
        @DisplayName("删除分享成功")
        void deleteShare_Success() throws Exception {
            doNothing().when(shareService).deleteShare(anyString(), anyLong());

            mockMvc.perform(delete("/shares/{token}", TEST_TOKEN))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));
        }

        @Test
        @DisplayName("删除分享 - 分享不存在")
        void deleteShare_NotFound() throws Exception {
            doThrow(new BusinessException(4004, "分享链接不存在"))
                    .when(shareService).deleteShare(anyString(), anyLong());

            mockMvc.perform(delete("/shares/{token}", TEST_TOKEN))
                    .andExpect(status().isGone())
                    .andExpect(jsonPath("$.code").value(4004));
        }
    }

    // ==================== togglePublic 测试 ====================

    @Nested
    @DisplayName("PUT /shares/{token}/public 测试")
    class TogglePublicTest {

        @Test
        @DisplayName("切换公开状态成功")
        void togglePublic_Success() throws Exception {
            doNothing().when(shareService).togglePublic(anyString(), anyLong());

            mockMvc.perform(put("/shares/{token}/public", TEST_TOKEN))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));
        }
    }

    // ==================== getShareDetail 测试 ====================

    @Nested
    @DisplayName("GET /share/{token} 测试")
    class GetShareDetailTest {

        @Test
        @DisplayName("获取分享详情成功")
        void getShareDetail_Success() throws Exception {
            ExperienceDetailResponse detail = ExperienceDetailResponse.builder()
                    .token(TEST_TOKEN)
                    .title("Java 面试")
                    .build();
            when(shareService.getShareDetail(anyString())).thenReturn(detail);

            mockMvc.perform(get("/share/{token}", TEST_TOKEN))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));
        }

        @Test
        @DisplayName("获取分享详情 - 分享不存在")
        void getShareDetail_NotFound() throws Exception {
            when(shareService.getShareDetail(anyString()))
                    .thenThrow(new BusinessException(4004, "分享链接不存在"));

            mockMvc.perform(get("/share/{token}", TEST_TOKEN))
                    .andExpect(status().isGone())
                    .andExpect(jsonPath("$.code").value(4004));
        }

        @Test
        @DisplayName("获取分享详情 - 分享已过期")
        void getShareDetail_Expired() throws Exception {
            when(shareService.getShareDetail(anyString()))
                    .thenThrow(new BusinessException(4003, "分享链接已过期"));

            mockMvc.perform(get("/share/{token}", TEST_TOKEN))
                    .andExpect(status().isGone())
                    .andExpect(jsonPath("$.code").value(4003));
        }
    }

    // ==================== 边界条件测试 ====================

    @Nested
    @DisplayName("边界条件测试")
    class BoundaryTest {

        @Test
        @DisplayName("创建分享 - 请求体为空")
        void createShare_EmptyBody() throws Exception {
            ShareResponse share = ShareResponse.builder()
                    .id(1L)
                    .token(TEST_TOKEN)
                    .build();
            when(shareService.createShare(anyLong(), anyLong(), any(), any())).thenReturn(share);

            mockMvc.perform(post("/interviews/{id}/share", TEST_INTERVIEW_ID)
                            .contentType("application/json")
                            .content("{}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));
        }

        @Test
        @DisplayName("获取分享列表 - 空列表")
        void listShares_Empty() throws Exception {
            when(shareService.listShares(anyLong(), anyLong())).thenReturn(List.of());

            mockMvc.perform(get("/interviews/{id}/shares", TEST_INTERVIEW_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.length()").value(0));
        }

        @Test
        @DisplayName("获取分享列表 - 多条记录")
        void listShares_Multiple() throws Exception {
            ShareResponse share1 = ShareResponse.builder()
                    .id(1L)
                    .token("token1")
                    .build();
            ShareResponse share2 = ShareResponse.builder()
                    .id(2L)
                    .token("token2")
                    .build();
            when(shareService.listShares(anyLong(), anyLong())).thenReturn(List.of(share1, share2));

            mockMvc.perform(get("/interviews/{id}/shares", TEST_INTERVIEW_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.length()").value(2));
        }

        @Test
        @DisplayName("切换公开状态 - 分享不存在")
        void togglePublic_NotFound() throws Exception {
            doThrow(new BusinessException(4004, "分享链接不存在"))
                    .when(shareService).togglePublic(anyString(), anyLong());

            mockMvc.perform(put("/shares/{token}/public", TEST_TOKEN))
                    .andExpect(status().isGone())
                    .andExpect(jsonPath("$.code").value(4004));
        }
    }
}
