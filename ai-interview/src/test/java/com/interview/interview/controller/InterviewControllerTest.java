package com.interview.interview.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.interview.common.exception.BusinessException;
import com.interview.common.exception.GlobalExceptionHandler;
import com.interview.common.result.PageResult;
import com.interview.interview.dto.InterviewDetailResponse;
import com.interview.interview.dto.InterviewListResponse;
import com.interview.interview.dto.InterviewStatusResponse;
import com.interview.interview.entity.Interview;
import com.interview.interview.service.InterviewService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
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
 * InterviewController 功能测试
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("InterviewController 功能测试")
class InterviewControllerTest {

    private MockMvc mockMvc;
    private ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private InterviewService interviewService;

    @InjectMocks
    private InterviewController interviewController;

    private static final Long TEST_USER_ID = 1L;
    private static final Long TEST_INTERVIEW_ID = 100L;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(interviewController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(TEST_USER_ID, "testuser",
                        List.of(new SimpleGrantedAuthority("ROLE_USER"))));
    }

    // ==================== upload 测试 ====================

    @Nested
    @DisplayName("POST /interviews/upload 测试")
    class UploadTest {

        @Test
        @DisplayName("上传面试成功")
        void upload_Success() throws Exception {
            MockMultipartFile audioFile = new MockMultipartFile(
                    "audioFile", "test.wav", "audio/wav", "audio content".getBytes());
            MockMultipartFile resumeFile = new MockMultipartFile(
                    "resumeFile", "resume.pdf", "application/pdf", "resume content".getBytes());

            when(interviewService.upload(anyLong(), any(), any(), anyString(),
                    any(), any(), any(), any(), any())).thenReturn(TEST_INTERVIEW_ID);

            mockMvc.perform(multipart("/interviews/upload")
                            .file(audioFile)
                            .file(resumeFile)
                            .param("jdText", "Java 后端开发")
                            .param("title", "面试标题")
                            .param("companyName", "阿里巴巴"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data").value(TEST_INTERVIEW_ID));
        }
    }

    // ==================== list 测试 ====================

    @Nested
    @DisplayName("GET /interviews 测试")
    class ListTest {

        @Test
        @DisplayName("获取面试列表成功")
        void list_Success() throws Exception {
            InterviewListResponse item = InterviewListResponse.builder()
                    .id(TEST_INTERVIEW_ID)
                    .title("Java 面试")
                    .companyName("阿里巴巴")
                    .status(3)
                    .overallScore(85)
                    .createdAt(LocalDateTime.now())
                    .build();
            PageResult<InterviewListResponse> pageResult = new PageResult<>(
                    List.of(item), 1, 1, 10);
            when(interviewService.list(anyLong(), anyInt(), anyInt(),
                    any(), any(), any(), any(), any())).thenReturn(pageResult);

            mockMvc.perform(get("/interviews")
                            .param("page", "1")
                            .param("size", "10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.total").value(1))
                    .andExpect(jsonPath("$.data.records[0].title").value("Java 面试"));
        }
    }

    // ==================== getDetail 测试 ====================

    @Nested
    @DisplayName("GET /interviews/{id} 测试")
    class GetDetailTest {

        @Test
        @DisplayName("获取面试详情成功")
        void getDetail_Success() throws Exception {
            InterviewDetailResponse detail = InterviewDetailResponse.builder()
                    .id(TEST_INTERVIEW_ID)
                    .title("Java 面试")
                    .companyName("阿里巴巴")
                    .overallScore(85)
                    .questions(List.of())
                    .build();
            when(interviewService.getDetail(anyLong(), anyLong())).thenReturn(detail);

            mockMvc.perform(get("/interviews/{id}", TEST_INTERVIEW_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.title").value("Java 面试"));
        }

        @Test
        @DisplayName("获取面试详情 - 面试不存在")
        void getDetail_NotFound() throws Exception {
            when(interviewService.getDetail(anyLong(), anyLong()))
                    .thenThrow(new BusinessException(2003, "面试记录不存在"));

            mockMvc.perform(get("/interviews/{id}", TEST_INTERVIEW_ID))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value(2003));
        }
    }

    // ==================== delete 测试 ====================

    @Nested
    @DisplayName("DELETE /interviews/{id} 测试")
    class DeleteTest {

        @Test
        @DisplayName("删除面试成功")
        void delete_Success() throws Exception {
            doNothing().when(interviewService).delete(anyLong(), anyLong());

            mockMvc.perform(delete("/interviews/{id}", TEST_INTERVIEW_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));
        }
    }

    // ==================== getStatus 测试 ====================

    @Nested
    @DisplayName("GET /interviews/{id}/status 测试")
    class GetStatusTest {

        @Test
        @DisplayName("查询处理进度成功")
        void getStatus_Success() throws Exception {
            InterviewStatusResponse statusResponse = InterviewStatusResponse.builder()
                    .status(1)
                    .processingStep(2)
                    .processingStepName("问题边界识别")
                    .progress(50)
                    .build();
            when(interviewService.getStatus(anyLong(), anyLong())).thenReturn(statusResponse);

            mockMvc.perform(get("/interviews/{id}/status", TEST_INTERVIEW_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value(1))
                    .andExpect(jsonPath("$.data.processingStep").value(2))
                    .andExpect(jsonPath("$.data.progress").value(50));
        }
    }

    // ==================== retry 测试 ====================

    @Nested
    @DisplayName("POST /interviews/{id}/retry 测试")
    class RetryTest {

        @Test
        @DisplayName("重试处理成功")
        void retry_Success() throws Exception {
            doNothing().when(interviewService).retry(anyLong(), anyLong());

            mockMvc.perform(post("/interviews/{id}/retry", TEST_INTERVIEW_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));
        }
    }
}
