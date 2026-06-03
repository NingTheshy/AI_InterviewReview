package com.interview.interview.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.interview.common.constant.ErrorCode;
import com.interview.common.exception.BusinessException;
import com.interview.interview.dto.ExperienceDetailResponse;
import com.interview.interview.entity.Interview;
import com.interview.interview.entity.InterviewShare;
import com.interview.interview.mapper.InterviewMapper;
import com.interview.interview.mapper.InterviewQuestionMapper;
import com.interview.interview.mapper.InterviewShareMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * ExperienceServiceImpl 单元测试
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ExperienceService 单元测试")
class ExperienceServiceImplTest {

    @Mock
    private InterviewShareMapper shareMapper;

    @Mock
    private InterviewMapper interviewMapper;

    @Mock
    private InterviewQuestionMapper questionMapper;

    @InjectMocks
    private ExperienceServiceImpl experienceService;

    private InterviewShare testShare;
    private Interview testInterview;
    private static final String TEST_TOKEN = "abc123def456";

    @BeforeEach
    void setUp() {
        testShare = new InterviewShare();
        testShare.setId(1L);
        testShare.setInterviewId(100L);
        testShare.setShareToken(TEST_TOKEN);
        testShare.setIsPublic(1);
        testShare.setViewCount(10);
        testShare.setExpireAt(null);

        testInterview = new Interview();
        testInterview.setId(100L);
        testInterview.setTitle("Java 面试");
        testInterview.setCompanyName("阿里巴巴");
    }

    // ==================== listPublic 测试 ====================

    @Nested
    @DisplayName("listPublic 方法测试")
    class ListPublicTest {

        @Test
        @DisplayName("查询公开面经列表成功")
        void listPublic_Success() {
            Page<InterviewShare> page = new Page<>(1, 10);
            page.setRecords(Collections.singletonList(testShare));
            page.setTotal(1);

            when(shareMapper.selectPage(any(), any(LambdaQueryWrapper.class))).thenReturn(page);
            when(interviewMapper.selectBatchIds(anyList())).thenReturn(Collections.singletonList(testInterview));
            when(questionMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(Collections.emptyList());

            var result = experienceService.listPublic(1, 10, null, null, null, null);

            assertNotNull(result);
            assertEquals(1, result.getRecords().size());
            assertEquals("Java 面试", result.getRecords().get(0).getTitle());
        }

        @Test
        @DisplayName("查询公开面经列表 - 按浏览量排序")
        void listPublic_SortByViewCount() {
            Page<InterviewShare> page = new Page<>(1, 10);
            page.setRecords(Collections.emptyList());
            page.setTotal(0);

            when(shareMapper.selectPage(any(), any(LambdaQueryWrapper.class))).thenReturn(page);

            var result = experienceService.listPublic(1, 10, null, null, null, "viewCount");

            assertNotNull(result);
        }
    }

    // ==================== getDetail 测试 ====================

    @Nested
    @DisplayName("getDetail 方法测试")
    class GetDetailTest {

        @Test
        @DisplayName("获取面经详情成功")
        void getDetail_Success() {
            when(shareMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(testShare);
            when(shareMapper.update(any(), any())).thenReturn(1);
            // 模拟原子更新后重新查询返回最新数据
            InterviewShare updatedShare = new InterviewShare();
            updatedShare.setId(1L);
            updatedShare.setInterviewId(100L);
            updatedShare.setShareToken(TEST_TOKEN);
            updatedShare.setIsPublic(1);
            updatedShare.setViewCount(11);
            updatedShare.setExpireAt(null);
            when(shareMapper.selectById(anyLong())).thenReturn(updatedShare);
            when(interviewMapper.selectById(100L)).thenReturn(testInterview);
            when(questionMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(Collections.emptyList());

            ExperienceDetailResponse result = experienceService.getDetail(TEST_TOKEN);

            assertNotNull(result);
            assertEquals("Java 面试", result.getTitle());
            assertEquals(11, result.getViewCount());
        }

        @Test
        @DisplayName("获取面经详情 - 面经不存在")
        void getDetail_NotFound() {
            when(shareMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

            assertThrows(BusinessException.class,
                    () -> experienceService.getDetail(TEST_TOKEN));
        }

        @Test
        @DisplayName("获取面经详情 - 面经已过期")
        void getDetail_Expired() {
            testShare.setExpireAt(LocalDateTime.now().minusDays(1));
            when(shareMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(testShare);

            assertThrows(BusinessException.class,
                    () -> experienceService.getDetail(TEST_TOKEN));
        }
    }
}
