package com.interview.interview.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.interview.common.constant.ErrorCode;
import com.interview.common.exception.BusinessException;
import com.interview.interview.entity.InterviewComment;
import com.interview.interview.entity.InterviewShare;
import com.interview.interview.mapper.InterviewCommentMapper;
import com.interview.interview.mapper.InterviewShareMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * CommentServiceImpl 单元测试
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CommentService 单元测试")
class CommentServiceImplTest {

    @Mock
    private InterviewCommentMapper commentMapper;

    @Mock
    private InterviewShareMapper shareMapper;

    @Mock
    private com.interview.auth.mapper.UserMapper userMapper;

    @InjectMocks
    private CommentServiceImpl commentService;

    private InterviewShare testShare;
    private InterviewComment testComment;
    private static final String TEST_TOKEN = "abc123def456";
    private static final Long TEST_USER_ID = 1L;
    private static final Long TEST_SHARE_ID = 10L;
    private static final Long TEST_COMMENT_ID = 20L;

    @BeforeEach
    void setUp() {
        testShare = new InterviewShare();
        testShare.setId(TEST_SHARE_ID);
        testShare.setShareToken(TEST_TOKEN);

        testComment = new InterviewComment();
        testComment.setId(TEST_COMMENT_ID);
        testComment.setShareId(TEST_SHARE_ID);
        testComment.setUserId(TEST_USER_ID);
        testComment.setContent("很好的面经分享！");
    }

    // ==================== listByShareToken 测试 ====================

    @Nested
    @DisplayName("listByShareToken 方法测试")
    class ListByShareTokenTest {

        @Test
        @DisplayName("获取评论列表成功")
        void listByShareToken_Success() {
            when(shareMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(testShare);

            Page<InterviewComment> page = new Page<>(1, 20);
            page.setRecords(Collections.singletonList(testComment));
            page.setTotal(1);
            when(commentMapper.selectPage(any(), any(LambdaQueryWrapper.class))).thenReturn(page);

            var result = commentService.listByShareToken(TEST_TOKEN, 1, 20);

            assertNotNull(result);
            assertEquals(1, result.getRecords().size());
        }

        @Test
        @DisplayName("获取评论列表 - 分享不存在")
        void listByShareToken_ShareNotFound() {
            when(shareMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

            assertThrows(BusinessException.class,
                    () -> commentService.listByShareToken(TEST_TOKEN, 1, 20));
        }
    }

    // ==================== addComment 测试 ====================

    @Nested
    @DisplayName("addComment 方法测试")
    class AddCommentTest {

        @Test
        @DisplayName("发表评论成功")
        void addComment_Success() {
            when(shareMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(testShare);
            when(commentMapper.insert(any(InterviewComment.class))).thenReturn(1);

            commentService.addComment(TEST_TOKEN, TEST_USER_ID, "很好的面经！");

            verify(commentMapper).insert(any(InterviewComment.class));
        }

        @Test
        @DisplayName("发表评论 - 内容为空")
        void addComment_Empty() {
            assertThrows(BusinessException.class,
                    () -> commentService.addComment(TEST_TOKEN, TEST_USER_ID, ""));
        }

        @Test
        @DisplayName("发表评论 - 内容为 null")
        void addComment_Null() {
            assertThrows(BusinessException.class,
                    () -> commentService.addComment(TEST_TOKEN, TEST_USER_ID, null));
        }

        @Test
        @DisplayName("发表评论 - 内容超长")
        void addComment_TooLong() {
            String longContent = "a".repeat(501);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> commentService.addComment(TEST_TOKEN, TEST_USER_ID, longContent));

            assertEquals(ErrorCode.COMMENT_TOO_LONG.getCode(), ex.getCode());
        }

        @Test
        @DisplayName("发表评论 - 分享不存在")
        void addComment_ShareNotFound() {
            when(shareMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

            assertThrows(BusinessException.class,
                    () -> commentService.addComment(TEST_TOKEN, TEST_USER_ID, "内容"));
        }
    }

    // ==================== deleteComment 测试 ====================

    @Nested
    @DisplayName("deleteComment 方法测试")
    class DeleteCommentTest {

        @Test
        @DisplayName("删除评论成功")
        void deleteComment_Success() {
            when(commentMapper.selectById(TEST_COMMENT_ID)).thenReturn(testComment);
            when(commentMapper.deleteById(TEST_COMMENT_ID)).thenReturn(1);

            commentService.deleteComment(TEST_COMMENT_ID, TEST_USER_ID);

            verify(commentMapper).deleteById(TEST_COMMENT_ID);
        }

        @Test
        @DisplayName("删除评论 - 评论不存在")
        void deleteComment_NotFound() {
            when(commentMapper.selectById(TEST_COMMENT_ID)).thenReturn(null);

            assertThrows(BusinessException.class,
                    () -> commentService.deleteComment(TEST_COMMENT_ID, TEST_USER_ID));
        }

        @Test
        @DisplayName("删除评论 - 无权限")
        void deleteComment_AccessDenied() {
            when(commentMapper.selectById(TEST_COMMENT_ID)).thenReturn(testComment);

            assertThrows(BusinessException.class,
                    () -> commentService.deleteComment(TEST_COMMENT_ID, 999L));
        }
    }
}
