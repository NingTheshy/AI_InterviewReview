package com.interview.interview.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.interview.common.constant.ErrorCode;
import com.interview.common.exception.BusinessException;
import com.interview.interview.dto.ExperienceDetailResponse;
import com.interview.interview.dto.ShareResponse;
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
 * ShareServiceImpl 单元测试
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ShareService 单元测试")
class ShareServiceImplTest {

    @Mock
    private InterviewShareMapper shareMapper;

    @Mock
    private InterviewMapper interviewMapper;

    @Mock
    private InterviewQuestionMapper questionMapper;

    @InjectMocks
    private ShareServiceImpl shareService;

    private Interview testInterview;
    private InterviewShare testShare;
    private static final Long TEST_USER_ID = 1L;
    private static final Long TEST_INTERVIEW_ID = 100L;
    private static final String TEST_TOKEN = "abc123def456";

    @BeforeEach
    void setUp() {
        testInterview = new Interview();
        testInterview.setId(TEST_INTERVIEW_ID);
        testInterview.setUserId(TEST_USER_ID);

        testShare = new InterviewShare();
        testShare.setId(1L);
        testShare.setInterviewId(TEST_INTERVIEW_ID);
        testShare.setUserId(TEST_USER_ID);
        testShare.setShareToken(TEST_TOKEN);
        testShare.setIsPublic(0);
        testShare.setViewCount(0);
    }

    // ==================== createShare 测试 ====================

    @Nested
    @DisplayName("createShare 方法测试")
    class CreateShareTest {

        @Test
        @DisplayName("创建分享成功 - 7天过期")
        void createShare_Success_7d() {
            when(interviewMapper.selectById(TEST_INTERVIEW_ID)).thenReturn(testInterview);
            when(shareMapper.insert(any(InterviewShare.class))).thenReturn(1);

            ShareResponse result = shareService.createShare(TEST_INTERVIEW_ID, TEST_USER_ID, "7d", false);

            assertNotNull(result);
            assertNotNull(result.getToken());
            assertEquals(0, result.getIsPublic());
            verify(shareMapper).insert(any(InterviewShare.class));
        }

        @Test
        @DisplayName("创建分享成功 - 永不过期")
        void createShare_Success_Never() {
            when(interviewMapper.selectById(TEST_INTERVIEW_ID)).thenReturn(testInterview);
            when(shareMapper.insert(any(InterviewShare.class))).thenReturn(1);

            ShareResponse result = shareService.createShare(TEST_INTERVIEW_ID, TEST_USER_ID, "never", true);

            assertNotNull(result);
            assertNull(result.getExpireAt());
            assertEquals(1, result.getIsPublic());
        }

        @Test
        @DisplayName("创建分享 - 面试不存在")
        void createShare_InterviewNotFound() {
            when(interviewMapper.selectById(TEST_INTERVIEW_ID)).thenReturn(null);

            assertThrows(BusinessException.class,
                    () -> shareService.createShare(TEST_INTERVIEW_ID, TEST_USER_ID, "7d", false));
        }

        @Test
        @DisplayName("创建分享 - 无权限")
        void createShare_AccessDenied() {
            when(interviewMapper.selectById(TEST_INTERVIEW_ID)).thenReturn(testInterview);

            assertThrows(BusinessException.class,
                    () -> shareService.createShare(TEST_INTERVIEW_ID, 999L, "7d", false));
        }
    }

    // ==================== listShares 测试 ====================

    @Nested
    @DisplayName("listShares 方法测试")
    class ListSharesTest {

        @Test
        @DisplayName("查询分享列表成功")
        void listShares_Success() {
            when(shareMapper.selectList(any(LambdaQueryWrapper.class)))
                    .thenReturn(Collections.singletonList(testShare));

            var result = shareService.listShares(TEST_INTERVIEW_ID, TEST_USER_ID);

            assertNotNull(result);
            assertEquals(1, result.size());
        }
    }

    // ==================== deleteShare 测试 ====================

    @Nested
    @DisplayName("deleteShare 方法测试")
    class DeleteShareTest {

        @Test
        @DisplayName("删除分享成功")
        void deleteShare_Success() {
            when(shareMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(testShare);
            when(shareMapper.deleteById(anyLong())).thenReturn(1);

            shareService.deleteShare(TEST_TOKEN, TEST_USER_ID);

            verify(shareMapper).deleteById(anyLong());
        }

        @Test
        @DisplayName("删除分享 - 分享不存在")
        void deleteShare_NotFound() {
            when(shareMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

            assertThrows(BusinessException.class,
                    () -> shareService.deleteShare(TEST_TOKEN, TEST_USER_ID));
        }
    }

    // ==================== togglePublic 测试 ====================

    @Nested
    @DisplayName("togglePublic 方法测试")
    class TogglePublicTest {

        @Test
        @DisplayName("切换公开状态 - 私有转公开")
        void togglePublic_PrivateToPublic() {
            testShare.setIsPublic(0);
            when(shareMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(testShare);
            when(shareMapper.updateById(any(InterviewShare.class))).thenReturn(1);

            shareService.togglePublic(TEST_TOKEN, TEST_USER_ID);

            assertEquals(1, testShare.getIsPublic());
        }

        @Test
        @DisplayName("切换公开状态 - 公开转私有")
        void togglePublic_PublicToPrivate() {
            testShare.setIsPublic(1);
            when(shareMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(testShare);
            when(shareMapper.updateById(any(InterviewShare.class))).thenReturn(1);

            shareService.togglePublic(TEST_TOKEN, TEST_USER_ID);

            assertEquals(0, testShare.getIsPublic());
        }

        @Test
        @DisplayName("切换公开状态 - 分享不存在")
        void togglePublic_NotFound() {
            when(shareMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

            assertThrows(BusinessException.class,
                    () -> shareService.togglePublic(TEST_TOKEN, TEST_USER_ID));
        }
    }

    // ==================== getShareDetail 测试 ====================

    @Nested
    @DisplayName("getShareDetail 方法测试")
    class GetShareDetailTest {

        @Test
        @DisplayName("获取分享详情成功")
        void getShareDetail_Success() {
            when(shareMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(testShare);
            when(shareMapper.update(any(), any())).thenReturn(1);
            when(shareMapper.selectById(anyLong())).thenReturn(testShare);
            when(interviewMapper.selectById(TEST_INTERVIEW_ID)).thenReturn(testInterview);
            when(questionMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(Collections.emptyList());

            ExperienceDetailResponse result = shareService.getShareDetail(TEST_TOKEN);

            assertNotNull(result);
            assertEquals(TEST_TOKEN, result.getToken());
        }

        @Test
        @DisplayName("获取分享详情 - 分享不存在")
        void getShareDetail_NotFound() {
            when(shareMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

            assertThrows(BusinessException.class,
                    () -> shareService.getShareDetail(TEST_TOKEN));
        }

        @Test
        @DisplayName("获取分享详情 - 分享已过期")
        void getShareDetail_Expired() {
            testShare.setExpireAt(LocalDateTime.now().minusDays(1));
            when(shareMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(testShare);

            assertThrows(BusinessException.class,
                    () -> shareService.getShareDetail(TEST_TOKEN));
        }
    }

    // ==================== 边界条件测试 ====================

    @Nested
    @DisplayName("边界条件测试")
    class BoundaryTest {

        @Test
        @DisplayName("创建分享 - 30天过期")
        void createShare_30d() {
            when(interviewMapper.selectById(TEST_INTERVIEW_ID)).thenReturn(testInterview);
            when(shareMapper.insert(any(InterviewShare.class))).thenReturn(1);

            ShareResponse result = shareService.createShare(TEST_INTERVIEW_ID, TEST_USER_ID, "30d", false);

            assertNotNull(result);
            assertNotNull(result.getExpireAt());
        }

        @Test
        @DisplayName("创建分享 - 默认过期类型")
        void createShare_DefaultExpireType() {
            when(interviewMapper.selectById(TEST_INTERVIEW_ID)).thenReturn(testInterview);
            when(shareMapper.insert(any(InterviewShare.class))).thenReturn(1);

            ShareResponse result = shareService.createShare(TEST_INTERVIEW_ID, TEST_USER_ID, null, false);

            assertNotNull(result);
            assertNull(result.getExpireAt());
        }

        @Test
        @DisplayName("创建分享 - isPublic 为 null")
        void createShare_NullIsPublic() {
            when(interviewMapper.selectById(TEST_INTERVIEW_ID)).thenReturn(testInterview);
            when(shareMapper.insert(any(InterviewShare.class))).thenReturn(1);

            ShareResponse result = shareService.createShare(TEST_INTERVIEW_ID, TEST_USER_ID, "7d", null);

            assertNotNull(result);
            assertEquals(0, result.getIsPublic());
        }

        @Test
        @DisplayName("同一面试创建多个分享")
        void createShare_MultipleShares() {
            when(interviewMapper.selectById(TEST_INTERVIEW_ID)).thenReturn(testInterview);
            when(shareMapper.insert(any(InterviewShare.class))).thenReturn(1);

            ShareResponse share1 = shareService.createShare(TEST_INTERVIEW_ID, TEST_USER_ID, "7d", false);
            ShareResponse share2 = shareService.createShare(TEST_INTERVIEW_ID, TEST_USER_ID, "30d", true);

            assertNotEquals(share1.getToken(), share2.getToken());
        }

        @Test
        @DisplayName("浏览量递增验证")
        void getShareDetail_ViewCountIncrement() {
            testShare.setViewCount(5);
            when(shareMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(testShare);
            when(shareMapper.update(any(), any())).thenReturn(1);
            // 模拟原子更新后重新查询返回最新数据
            InterviewShare updatedShare = new InterviewShare();
            updatedShare.setId(1L);
            updatedShare.setInterviewId(TEST_INTERVIEW_ID);
            updatedShare.setUserId(TEST_USER_ID);
            updatedShare.setShareToken(TEST_TOKEN);
            updatedShare.setIsPublic(0);
            updatedShare.setViewCount(6);
            when(shareMapper.selectById(anyLong())).thenReturn(updatedShare);
            when(interviewMapper.selectById(TEST_INTERVIEW_ID)).thenReturn(testInterview);
            when(questionMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(Collections.emptyList());

            ExperienceDetailResponse result = shareService.getShareDetail(TEST_TOKEN);

            assertNotNull(result);
            assertEquals(6, result.getViewCount());
        }

        @Test
        @DisplayName("切换公开状态 - 连续切换两次回到原状态")
        void togglePublic_DoubleToggle() {
            testShare.setIsPublic(0);
            when(shareMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(testShare);
            when(shareMapper.updateById(any(InterviewShare.class))).thenReturn(1);

            shareService.togglePublic(TEST_TOKEN, TEST_USER_ID);
            assertEquals(1, testShare.getIsPublic());

            shareService.togglePublic(TEST_TOKEN, TEST_USER_ID);
            assertEquals(0, testShare.getIsPublic());
        }

        @Test
        @DisplayName("删除分享 - 不同用户无法删除")
        void deleteShare_WrongUser() {
            when(shareMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

            assertThrows(BusinessException.class,
                    () -> shareService.deleteShare(TEST_TOKEN, 999L));
        }

        @Test
        @DisplayName("查询分享列表 - 空列表")
        void listShares_Empty() {
            when(shareMapper.selectList(any(LambdaQueryWrapper.class)))
                    .thenReturn(Collections.emptyList());

            var result = shareService.listShares(TEST_INTERVIEW_ID, TEST_USER_ID);

            assertNotNull(result);
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("永不过期的分享详情可正常访问")
        void getShareDetail_NeverExpire() {
            testShare.setExpireAt(null);
            when(shareMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(testShare);
            when(shareMapper.update(any(), any())).thenReturn(1);
            when(shareMapper.selectById(anyLong())).thenReturn(testShare);
            when(interviewMapper.selectById(TEST_INTERVIEW_ID)).thenReturn(testInterview);
            when(questionMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(Collections.emptyList());

            ExperienceDetailResponse result = shareService.getShareDetail(TEST_TOKEN);

            assertNotNull(result);
        }
    }
}
