package com.interview.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.interview.admin.dto.*;
import com.interview.auth.entity.User;
import com.interview.auth.mapper.UserMapper;
import com.interview.common.constant.UserStatus;
import com.interview.common.exception.BusinessException;
import com.interview.common.result.PageResult;
import com.interview.interview.entity.Interview;
import com.interview.interview.entity.InterviewComment;
import com.interview.interview.entity.InterviewShare;
import com.interview.interview.mapper.InterviewCommentMapper;
import com.interview.interview.mapper.InterviewMapper;
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
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * AdminServiceImpl 单元测试
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AdminService 单元测试")
class AdminServiceImplTest {

    @Mock
    private UserMapper userMapper;

    @Mock
    private InterviewMapper interviewMapper;

    @Mock
    private InterviewShareMapper interviewShareMapper;

    @Mock
    private InterviewCommentMapper interviewCommentMapper;

    @InjectMocks
    private AdminServiceImpl adminService;

    private User testUser;
    private static final Long TEST_USER_ID = 1L;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId(TEST_USER_ID);
        testUser.setUsername("testuser");
        testUser.setEmail("test@example.com");
        testUser.setNickname("Test User");
        testUser.setRole(0);
        testUser.setStatus(UserStatus.ACTIVE.getCode());
        testUser.setLoginCount(10);
        testUser.setLastLoginAt(LocalDateTime.now());
        testUser.setCreatedAt(LocalDateTime.now().minusDays(30));
    }

    // ==================== 用户管理测试 ====================

    @Nested
    @DisplayName("用户管理方法测试")
    class UserManagementTest {

        @Test
        @DisplayName("获取用户列表成功")
        void listUsers_Success() {
            // Arrange
            Page<User> page = new Page<>(1, 10);
            page.setRecords(Arrays.asList(testUser));
            page.setTotal(1);

            when(userMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(page);

            // Act
            PageResult<UserManagementResponse> result = adminService.listUsers(1, 10, null, null);

            // Assert
            assertNotNull(result);
            assertEquals(1, result.getRecords().size());
            assertEquals(1, result.getTotal());
        }

        @Test
        @DisplayName("获取用户详情成功")
        void getUserDetail_Success() {
            // Arrange
            when(userMapper.selectById(TEST_USER_ID)).thenReturn(testUser);

            // Act
            UserManagementResponse response = adminService.getUserDetail(TEST_USER_ID);

            // Assert
            assertNotNull(response);
            assertEquals(TEST_USER_ID, response.getId());
            assertEquals("testuser", response.getUsername());
        }

        @Test
        @DisplayName("获取用户详情 - 用户不存在")
        void getUserDetail_UserNotFound() {
            // Arrange
            when(userMapper.selectById(TEST_USER_ID)).thenReturn(null);

            // Act & Assert
            assertThrows(BusinessException.class,
                    () -> adminService.getUserDetail(TEST_USER_ID));
        }

        @Test
        @DisplayName("更新用户状态成功")
        void updateUserStatus_Success() {
            // Arrange
            when(userMapper.selectById(TEST_USER_ID)).thenReturn(testUser);
            when(userMapper.updateById(any(User.class))).thenReturn(1);

            // Act
            adminService.updateUserStatus(TEST_USER_ID, UserStatus.DISABLED.getCode());

            // Assert
            verify(userMapper).updateById(any(User.class));
        }

        @Test
        @DisplayName("更新用户状态 - 用户不存在")
        void updateUserStatus_UserNotFound() {
            // Arrange
            when(userMapper.selectById(TEST_USER_ID)).thenReturn(null);

            // Act & Assert
            assertThrows(BusinessException.class,
                    () -> adminService.updateUserStatus(TEST_USER_ID, 0));
        }

        @Test
        @DisplayName("更新用户角色成功")
        void updateUserRole_Success() {
            // Arrange
            when(userMapper.selectById(TEST_USER_ID)).thenReturn(testUser);
            when(userMapper.updateById(any(User.class))).thenReturn(1);

            // Act
            adminService.updateUserRole(TEST_USER_ID, 1);

            // Assert
            verify(userMapper).updateById(any(User.class));
        }

        @Test
        @DisplayName("更新用户角色 - 用户不存在")
        void updateUserRole_UserNotFound() {
            // Arrange
            when(userMapper.selectById(TEST_USER_ID)).thenReturn(null);

            // Act & Assert
            assertThrows(BusinessException.class,
                    () -> adminService.updateUserRole(TEST_USER_ID, 1));
        }
    }

    // ==================== 系统统计测试 ====================

    @Nested
    @DisplayName("系统统计方法测试")
    class StatsTest {

        @Test
        @DisplayName("获取系统统计成功")
        void getStatsOverview_Success() {
            // Arrange
            when(userMapper.selectCount(isNull())).thenReturn(100L);
            when(interviewMapper.selectCount(isNull())).thenReturn(500L);
            when(interviewMapper.selectCount(any(LambdaQueryWrapper.class)))
                    .thenReturn(10L)   // status=0 (processing)
                    .thenReturn(470L)  // status=1 (completed)
                    .thenReturn(12L)   // status=2 (failed)
                    .thenReturn(0L);   // score query (no completed with scores)

            // Mock for completed interviews with scores
            Interview completedInterview = new Interview();
            completedInterview.setOverallScore(85);
            when(interviewMapper.selectList(any(LambdaQueryWrapper.class)))
                    .thenReturn(Arrays.asList(completedInterview));

            when(interviewShareMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(50L);
            when(interviewCommentMapper.selectCount(isNull())).thenReturn(200L);

            // Act
            StatsOverviewResponse response = adminService.getStatsOverview();

            // Assert
            assertNotNull(response);
            assertEquals(100L, response.getTotalUsers());
            assertEquals(500L, response.getTotalInterviews());
            assertEquals(50L, response.getTotalExperiences());
            assertEquals(200L, response.getTotalComments());
            assertNotNull(response.getStatusDistribution());
            assertEquals(3, response.getStatusDistribution().size());
            assertNotNull(response.getAverageScore());
        }
    }

    // ==================== 面试管理测试 ====================

    @Nested
    @DisplayName("面试管理方法测试")
    class InterviewManagementTest {

        private Interview testInterview;

        @BeforeEach
        void setUp() {
            testInterview = new Interview();
            testInterview.setId(100L);
            testInterview.setUserId(TEST_USER_ID);
            testInterview.setTitle("Java后端面试");
            testInterview.setCompanyName("阿里巴巴");
            testInterview.setPositionTitle("高级工程师");
            testInterview.setStatus(3);
            testInterview.setOverallScore(85);
            testInterview.setCreatedAt(LocalDateTime.now());
        }

        @Test
        @DisplayName("获取面试列表成功")
        void listInterviews_Success() {
            // Arrange
            Page<Interview> page = new Page<>(1, 10);
            page.setRecords(Arrays.asList(testInterview));
            page.setTotal(1);

            when(interviewMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(page);
            when(userMapper.selectList(any(QueryWrapper.class)))
                    .thenReturn(Arrays.asList(testUser));

            // Act
            PageResult<InterviewManagementResponse> result = adminService.listInterviews(1, 10, null, null);

            // Assert
            assertNotNull(result);
            assertEquals(1, result.getRecords().size());
            assertEquals("Java后端面试", result.getRecords().get(0).getTitle());
        }

        @Test
        @DisplayName("获取面试列表 - 按用户筛选")
        void listInterviews_ByUserId() {
            // Arrange
            Page<Interview> page = new Page<>(1, 10);
            page.setRecords(Arrays.asList(testInterview));
            page.setTotal(1);

            when(interviewMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(page);
            when(userMapper.selectList(any(QueryWrapper.class)))
                    .thenReturn(Arrays.asList(testUser));

            // Act
            PageResult<InterviewManagementResponse> result = adminService.listInterviews(1, 10, TEST_USER_ID, null);

            // Assert
            assertNotNull(result);
            assertEquals(1, result.getRecords().size());
        }

        @Test
        @DisplayName("获取面试列表 - 按状态筛选")
        void listInterviews_ByStatus() {
            // Arrange
            Page<Interview> page = new Page<>(1, 10);
            page.setRecords(Arrays.asList(testInterview));
            page.setTotal(1);

            when(interviewMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(page);
            when(userMapper.selectList(any(QueryWrapper.class)))
                    .thenReturn(Arrays.asList(testUser));

            // Act
            PageResult<InterviewManagementResponse> result = adminService.listInterviews(1, 10, null, 3);

            // Assert
            assertNotNull(result);
            assertEquals(1, result.getRecords().size());
        }

        @Test
        @DisplayName("获取面试列表 - 空列表")
        void listInterviews_Empty() {
            // Arrange
            Page<Interview> page = new Page<>(1, 10);
            page.setRecords(Collections.emptyList());
            page.setTotal(0);

            when(interviewMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(page);

            // Act
            PageResult<InterviewManagementResponse> result = adminService.listInterviews(1, 10, null, null);

            // Assert
            assertNotNull(result);
            assertTrue(result.getRecords().isEmpty());
        }
    }

    // ==================== 面经管理测试 ====================

    @Nested
    @DisplayName("面经管理方法测试")
    class ExperienceManagementTest {

        private InterviewShare testShare;

        @BeforeEach
        void setUp() {
            testShare = new InterviewShare();
            testShare.setId(1L);
            testShare.setInterviewId(100L);
            testShare.setUserId(TEST_USER_ID);
            testShare.setShareToken("testtoken123");
            testShare.setIsPublic(1);
            testShare.setViewCount(100);
            testShare.setCreatedAt(LocalDateTime.now());
        }

        @Test
        @DisplayName("获取面经列表成功")
        void listExperiences_Success() {
            // Arrange
            Page<InterviewShare> page = new Page<>(1, 10);
            page.setRecords(Arrays.asList(testShare));
            page.setTotal(1);

            when(interviewShareMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(page);
            when(userMapper.selectList(any(QueryWrapper.class)))
                    .thenReturn(Arrays.asList(testUser));

            // Act
            PageResult<ExperienceManagementResponse> result = adminService.listExperiences(1, 10);

            // Assert
            assertNotNull(result);
            assertEquals(1, result.getRecords().size());
            assertEquals("testtoken123", result.getRecords().get(0).getShareToken());
        }

        @Test
        @DisplayName("获取面经列表 - 空列表")
        void listExperiences_Empty() {
            // Arrange
            Page<InterviewShare> page = new Page<>(1, 10);
            page.setRecords(Collections.emptyList());
            page.setTotal(0);

            when(interviewShareMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(page);

            // Act
            PageResult<ExperienceManagementResponse> result = adminService.listExperiences(1, 10);

            // Assert
            assertNotNull(result);
            assertTrue(result.getRecords().isEmpty());
        }

        @Test
        @DisplayName("设置面经状态成功")
        void setExperienceStatus_Success() {
            // Arrange
            testShare.setIsPublic(1);
            when(interviewShareMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(testShare);
            when(interviewShareMapper.updateById(any(InterviewShare.class))).thenReturn(1);

            // Act
            adminService.setExperienceStatus("testtoken123", 0);

            // Assert
            assertEquals(0, testShare.getIsPublic());
            verify(interviewShareMapper).updateById(any(InterviewShare.class));
        }

        @Test
        @DisplayName("设置面经状态 - 分享不存在")
        void setExperienceStatus_NotFound() {
            // Arrange
            when(interviewShareMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

            // Act & Assert
            assertThrows(BusinessException.class,
                    () -> adminService.setExperienceStatus("nonexistent", 0));
        }
    }

    // ==================== 评论管理测试 ====================

    @Nested
    @DisplayName("评论管理方法测试")
    class CommentManagementTest {

        private InterviewComment testComment;

        @BeforeEach
        void setUp() {
            testComment = new InterviewComment();
            testComment.setId(1L);
            testComment.setShareId(10L);
            testComment.setUserId(TEST_USER_ID);
            testComment.setContent("很好的面试分享！");
            testComment.setCreatedAt(LocalDateTime.now());
        }

        @Test
        @DisplayName("获取评论列表成功")
        void listComments_Success() {
            // Arrange
            Page<InterviewComment> page = new Page<>(1, 10);
            page.setRecords(Arrays.asList(testComment));
            page.setTotal(1);

            when(interviewCommentMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(page);
            when(userMapper.selectList(any(QueryWrapper.class)))
                    .thenReturn(Arrays.asList(testUser));

            // Act
            PageResult<CommentManagementResponse> result = adminService.listComments(1, 10, null);

            // Assert
            assertNotNull(result);
            assertEquals(1, result.getRecords().size());
            assertEquals("很好的面试分享！", result.getRecords().get(0).getContent());
        }

        @Test
        @DisplayName("获取评论列表 - 按用户筛选")
        void listComments_ByUserId() {
            // Arrange
            Page<InterviewComment> page = new Page<>(1, 10);
            page.setRecords(Arrays.asList(testComment));
            page.setTotal(1);

            when(interviewCommentMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(page);
            when(userMapper.selectList(any(QueryWrapper.class)))
                    .thenReturn(Arrays.asList(testUser));

            // Act
            PageResult<CommentManagementResponse> result = adminService.listComments(1, 10, TEST_USER_ID);

            // Assert
            assertNotNull(result);
            assertEquals(1, result.getRecords().size());
        }

        @Test
        @DisplayName("获取评论列表 - 空列表")
        void listComments_Empty() {
            // Arrange
            Page<InterviewComment> page = new Page<>(1, 10);
            page.setRecords(Collections.emptyList());
            page.setTotal(0);

            when(interviewCommentMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(page);

            // Act
            PageResult<CommentManagementResponse> result = adminService.listComments(1, 10, null);

            // Assert
            assertNotNull(result);
            assertTrue(result.getRecords().isEmpty());
        }

        @Test
        @DisplayName("删除评论成功")
        void deleteComment_Success() {
            // Arrange
            when(interviewCommentMapper.selectById(1L)).thenReturn(testComment);
            when(interviewCommentMapper.deleteById(1L)).thenReturn(1);

            // Act
            adminService.deleteComment(1L);

            // Assert
            verify(interviewCommentMapper).deleteById(1L);
        }

        @Test
        @DisplayName("删除评论 - 评论不存在")
        void deleteComment_NotFound() {
            // Arrange
            when(interviewCommentMapper.selectById(999L)).thenReturn(null);

            // Act & Assert
            assertThrows(BusinessException.class,
                    () -> adminService.deleteComment(999L));
        }
    }
}
