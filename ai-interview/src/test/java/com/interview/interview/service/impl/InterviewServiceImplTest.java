package com.interview.interview.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.interview.common.constant.ErrorCode;
import com.interview.common.constant.InterviewStatus;
import com.interview.common.exception.BusinessException;
import com.interview.interview.dto.InterviewDetailResponse;
import com.interview.interview.dto.InterviewListResponse;
import com.interview.interview.dto.InterviewStatusResponse;
import com.interview.interview.entity.Interview;
import com.interview.interview.mapper.InterviewMapper;
import com.interview.interview.mapper.InterviewQuestionMapper;
import com.interview.interview.service.InterviewAsyncService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * InterviewServiceImpl 单元测试
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("InterviewService 单元测试")
class InterviewServiceImplTest {

    @Mock
    private InterviewMapper interviewMapper;

    @Mock
    private InterviewQuestionMapper questionMapper;

    @Mock
    private InterviewAsyncService interviewAsyncService;

    @InjectMocks
    private InterviewServiceImpl interviewService;

    private Interview testInterview;
    private static final Long TEST_USER_ID = 1L;
    private static final Long TEST_INTERVIEW_ID = 100L;
    private Path tempDir;

    @BeforeEach
    void setUp() throws IOException {
        tempDir = Files.createTempDirectory("upload-test");
        ReflectionTestUtils.setField(interviewService, "uploadDir", tempDir.toString());
        ReflectionTestUtils.setField(interviewService, "baseUrl", "/files");

        testInterview = new Interview();
        testInterview.setId(TEST_INTERVIEW_ID);
        testInterview.setUserId(TEST_USER_ID);
        testInterview.setTitle("Java 后端面试");
        testInterview.setCompanyName("阿里巴巴");
        testInterview.setStatus(InterviewStatus.COMPLETED.getCode());
    }

    @AfterEach
    void tearDown() throws IOException {
        if (tempDir != null) {
            Files.walk(tempDir)
                    .sorted((a, b) -> b.compareTo(a))
                    .forEach(p -> {
                        try { Files.deleteIfExists(p); } catch (IOException ignored) {}
                    });
        }
    }

    // ==================== upload 测试 ====================

    @Nested
    @DisplayName("upload 方法测试")
    class UploadTest {

        @Test
        @DisplayName("上传面试成功")
        void upload_Success() {
            MockMultipartFile audioFile = new MockMultipartFile(
                    "audio", "test.wav", "audio/wav", "audio content".getBytes());
            MockMultipartFile resumeFile = new MockMultipartFile(
                    "resume", "resume.pdf", "application/pdf", "resume content".getBytes());

            doAnswer(invocation -> {
                Interview arg = invocation.getArgument(0);
                arg.setId(TEST_INTERVIEW_ID);
                return 1;
            }).when(interviewMapper).insert(any(Interview.class));

            Long id = interviewService.upload(TEST_USER_ID, audioFile, resumeFile, "JD文本",
                    "面试标题", "阿里巴巴", "Java开发", "互联网", "coding");

            assertNotNull(id);
            assertEquals(TEST_INTERVIEW_ID, id);
            verify(interviewAsyncService).processInterview(anyLong());
        }

        @Test
        @DisplayName("上传面试 - 最小参数（仅必填字段）")
        void upload_MinimalParams() {
            MockMultipartFile audioFile = new MockMultipartFile(
                    "audio", "test.wav", "audio/wav", "audio content".getBytes());
            MockMultipartFile resumeFile = new MockMultipartFile(
                    "resume", "resume.pdf", "application/pdf", "resume content".getBytes());

            doAnswer(invocation -> {
                Interview arg = invocation.getArgument(0);
                arg.setId(TEST_INTERVIEW_ID);
                return 1;
            }).when(interviewMapper).insert(any(Interview.class));

            Long id = interviewService.upload(TEST_USER_ID, audioFile, resumeFile, "JD文本",
                    null, null, null, null, null);

            assertNotNull(id);
            verify(interviewAsyncService).processInterview(anyLong());
        }
    }

    // ==================== list 测试 ====================

    @Nested
    @DisplayName("list 方法测试")
    class ListTest {

        @Test
        @DisplayName("查询面试列表成功")
        void list_Success() {
            Page<Interview> page = new Page<>(1, 10);
            page.setRecords(Collections.singletonList(testInterview));
            page.setTotal(1);

            when(interviewMapper.selectPage(any(), any(LambdaQueryWrapper.class))).thenReturn(page);

            var result = interviewService.list(TEST_USER_ID, 1, 10,
                    null, null, null, null, null);

            assertNotNull(result);
            assertEquals(1, result.getRecords().size());
            assertEquals(1, result.getTotal());
        }

        @Test
        @DisplayName("查询面试列表 - 带筛选条件")
        void list_WithFilters() {
            Page<Interview> page = new Page<>(1, 10);
            page.setRecords(Collections.emptyList());
            page.setTotal(0);

            when(interviewMapper.selectPage(any(), any(LambdaQueryWrapper.class))).thenReturn(page);

            var result = interviewService.list(TEST_USER_ID, 1, 10,
                    "阿里巴巴", "Java开发", "互联网", "coding", "createdAt");

            assertNotNull(result);
            assertEquals(0, result.getTotal());
        }
    }

    // ==================== getDetail 测试 ====================

    @Nested
    @DisplayName("getDetail 方法测试")
    class GetDetailTest {

        @Test
        @DisplayName("获取面试详情成功")
        void getDetail_Success() {
            when(interviewMapper.selectById(TEST_INTERVIEW_ID)).thenReturn(testInterview);
            when(questionMapper.selectList(any(LambdaQueryWrapper.class)))
                    .thenReturn(Collections.emptyList());

            InterviewDetailResponse result = interviewService.getDetail(TEST_INTERVIEW_ID, TEST_USER_ID);

            assertNotNull(result);
            assertEquals(TEST_INTERVIEW_ID, result.getId());
        }

        @Test
        @DisplayName("获取面试详情 - 面试不存在")
        void getDetail_NotFound() {
            when(interviewMapper.selectById(TEST_INTERVIEW_ID)).thenReturn(null);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> interviewService.getDetail(TEST_INTERVIEW_ID, TEST_USER_ID));

            assertEquals(ErrorCode.INTERVIEW_NOT_FOUND.getCode(), ex.getCode());
        }

        @Test
        @DisplayName("获取面试详情 - 无权限")
        void getDetail_AccessDenied() {
            when(interviewMapper.selectById(TEST_INTERVIEW_ID)).thenReturn(testInterview);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> interviewService.getDetail(TEST_INTERVIEW_ID, 999L));

            assertEquals(ErrorCode.INTERVIEW_ACCESS_DENIED.getCode(), ex.getCode());
        }
    }

    // ==================== delete 测试 ====================

    @Nested
    @DisplayName("delete 方法测试")
    class DeleteTest {

        @Test
        @DisplayName("删除面试成功")
        void delete_Success() {
            when(interviewMapper.selectById(TEST_INTERVIEW_ID)).thenReturn(testInterview);
            when(interviewMapper.deleteById(TEST_INTERVIEW_ID)).thenReturn(1);

            interviewService.delete(TEST_INTERVIEW_ID, TEST_USER_ID);

            verify(interviewMapper).deleteById(TEST_INTERVIEW_ID);
        }

        @Test
        @DisplayName("删除面试 - 面试不存在")
        void delete_NotFound() {
            when(interviewMapper.selectById(TEST_INTERVIEW_ID)).thenReturn(null);

            assertThrows(BusinessException.class,
                    () -> interviewService.delete(TEST_INTERVIEW_ID, TEST_USER_ID));
        }

        @Test
        @DisplayName("删除面试 - 无权限")
        void delete_AccessDenied() {
            when(interviewMapper.selectById(TEST_INTERVIEW_ID)).thenReturn(testInterview);

            assertThrows(BusinessException.class,
                    () -> interviewService.delete(TEST_INTERVIEW_ID, 999L));
        }
    }

    // ==================== getStatus 测试 ====================

    @Nested
    @DisplayName("getStatus 方法测试")
    class GetStatusTest {

        @Test
        @DisplayName("查询处理进度成功")
        void getStatus_Success() {
            when(interviewMapper.selectById(TEST_INTERVIEW_ID)).thenReturn(testInterview);

            InterviewStatusResponse result = interviewService.getStatus(TEST_INTERVIEW_ID, TEST_USER_ID);

            assertNotNull(result);
            assertEquals(testInterview.getStatus(), result.getStatus());
            assertNotNull(result.getProcessingStepName());
            assertNotNull(result.getProgress());
        }

        @Test
        @DisplayName("查询处理进度 - 面试不存在")
        void getStatus_NotFound() {
            when(interviewMapper.selectById(TEST_INTERVIEW_ID)).thenReturn(null);

            assertThrows(BusinessException.class,
                    () -> interviewService.getStatus(TEST_INTERVIEW_ID, TEST_USER_ID));
        }

        @Test
        @DisplayName("查询处理进度 - 无权限")
        void getStatus_AccessDenied() {
            when(interviewMapper.selectById(TEST_INTERVIEW_ID)).thenReturn(testInterview);

            assertThrows(BusinessException.class,
                    () -> interviewService.getStatus(TEST_INTERVIEW_ID, 999L));
        }
    }

    // ==================== retry 测试 ====================

    @Nested
    @DisplayName("retry 方法测试")
    class RetryTest {

        @Test
        @DisplayName("重试处理成功")
        void retry_Success() {
            testInterview.setStatus(InterviewStatus.FAILED.getCode());
            when(interviewMapper.selectById(TEST_INTERVIEW_ID)).thenReturn(testInterview);
            when(interviewMapper.updateById(any(Interview.class))).thenReturn(1);

            interviewService.retry(TEST_INTERVIEW_ID, TEST_USER_ID);

            verify(interviewMapper).updateById(any(Interview.class));
            verify(interviewAsyncService).processInterview(TEST_INTERVIEW_ID);
        }

        @Test
        @DisplayName("重试处理 - 面试不存在")
        void retry_NotFound() {
            when(interviewMapper.selectById(TEST_INTERVIEW_ID)).thenReturn(null);

            assertThrows(BusinessException.class,
                    () -> interviewService.retry(TEST_INTERVIEW_ID, TEST_USER_ID));
        }

        @Test
        @DisplayName("重试处理 - 无权限")
        void retry_AccessDenied() {
            when(interviewMapper.selectById(TEST_INTERVIEW_ID)).thenReturn(testInterview);

            assertThrows(BusinessException.class,
                    () -> interviewService.retry(TEST_INTERVIEW_ID, 999L));
        }
    }
}
