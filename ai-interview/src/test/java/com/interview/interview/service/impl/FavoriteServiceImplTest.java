package com.interview.interview.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.interview.common.constant.ErrorCode;
import com.interview.common.exception.BusinessException;
import com.interview.interview.entity.InterviewFavorite;
import com.interview.interview.mapper.InterviewFavoriteMapper;
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
 * FavoriteServiceImpl 单元测试
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("FavoriteService 单元测试")
class FavoriteServiceImplTest {

    @Mock
    private InterviewFavoriteMapper favoriteMapper;

    @InjectMocks
    private FavoriteServiceImpl favoriteService;

    private InterviewFavorite testFavorite;
    private static final Long TEST_USER_ID = 1L;
    private static final Long TEST_INTERVIEW_ID = 100L;
    private static final Long TEST_QUESTION_ID = 10L;
    private static final Long TEST_FAVORITE_ID = 200L;

    @BeforeEach
    void setUp() {
        testFavorite = new InterviewFavorite();
        testFavorite.setId(TEST_FAVORITE_ID);
        testFavorite.setUserId(TEST_USER_ID);
        testFavorite.setInterviewId(TEST_INTERVIEW_ID);
        testFavorite.setQuestionId(TEST_QUESTION_ID);
    }

    // ==================== addFavorite 测试 ====================

    @Nested
    @DisplayName("addFavorite 方法测试")
    class AddFavoriteTest {

        @Test
        @DisplayName("添加收藏成功")
        void addFavorite_Success() {
            when(favoriteMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
            when(favoriteMapper.insert(any(InterviewFavorite.class))).thenReturn(1);

            favoriteService.addFavorite(TEST_USER_ID, TEST_INTERVIEW_ID, TEST_QUESTION_ID, "好问题");

            verify(favoriteMapper).insert(any(InterviewFavorite.class));
        }

        @Test
        @DisplayName("添加收藏 - 已收藏")
        void addFavorite_AlreadyExists() {
            when(favoriteMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> favoriteService.addFavorite(TEST_USER_ID, TEST_INTERVIEW_ID, TEST_QUESTION_ID, null));

            assertEquals(ErrorCode.FAVORITE_EXISTS.getCode(), ex.getCode());
        }

        @Test
        @DisplayName("添加收藏 - 无问题 ID（收藏整个面试）")
        void addFavorite_WithoutQuestionId() {
            when(favoriteMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
            when(favoriteMapper.insert(any(InterviewFavorite.class))).thenReturn(1);

            favoriteService.addFavorite(TEST_USER_ID, TEST_INTERVIEW_ID, null, null);

            verify(favoriteMapper).insert(any(InterviewFavorite.class));
        }
    }

    // ==================== removeFavorite 测试 ====================

    @Nested
    @DisplayName("removeFavorite 方法测试")
    class RemoveFavoriteTest {

        @Test
        @DisplayName("取消收藏成功")
        void removeFavorite_Success() {
            when(favoriteMapper.selectById(TEST_FAVORITE_ID)).thenReturn(testFavorite);
            when(favoriteMapper.deleteById(TEST_FAVORITE_ID)).thenReturn(1);

            favoriteService.removeFavorite(TEST_FAVORITE_ID, TEST_USER_ID);

            verify(favoriteMapper).deleteById(TEST_FAVORITE_ID);
        }

        @Test
        @DisplayName("取消收藏 - 收藏不存在")
        void removeFavorite_NotFound() {
            when(favoriteMapper.selectById(TEST_FAVORITE_ID)).thenReturn(null);

            assertThrows(BusinessException.class,
                    () -> favoriteService.removeFavorite(TEST_FAVORITE_ID, TEST_USER_ID));
        }

        @Test
        @DisplayName("取消收藏 - 无权限")
        void removeFavorite_AccessDenied() {
            when(favoriteMapper.selectById(TEST_FAVORITE_ID)).thenReturn(testFavorite);

            assertThrows(BusinessException.class,
                    () -> favoriteService.removeFavorite(TEST_FAVORITE_ID, 999L));
        }
    }

    // ==================== list 测试 ====================

    @Nested
    @DisplayName("list 方法测试")
    class ListTest {

        @Test
        @DisplayName("查询收藏列表成功")
        void list_Success() {
            Page<InterviewFavorite> page = new Page<>(1, 10);
            page.setRecords(Collections.singletonList(testFavorite));
            page.setTotal(1);

            when(favoriteMapper.selectPage(any(), any(LambdaQueryWrapper.class))).thenReturn(page);

            var result = favoriteService.list(TEST_USER_ID, 1, 10);

            assertNotNull(result);
            assertEquals(1, result.getRecords().size());
        }

        @Test
        @DisplayName("查询收藏列表 - 空列表")
        void list_Empty() {
            Page<InterviewFavorite> page = new Page<>(1, 10);
            page.setRecords(Collections.emptyList());
            page.setTotal(0);

            when(favoriteMapper.selectPage(any(), any(LambdaQueryWrapper.class))).thenReturn(page);

            var result = favoriteService.list(TEST_USER_ID, 1, 10);

            assertNotNull(result);
            assertEquals(0, result.getRecords().size());
            assertEquals(0, result.getTotal());
        }
    }

    // ==================== 边界条件测试 ====================

    @Nested
    @DisplayName("边界条件测试")
    class BoundaryTest {

        @Test
        @DisplayName("添加收藏 - 达到上限")
        void addFavorite_LimitReached() {
            when(favoriteMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(100L);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> favoriteService.addFavorite(TEST_USER_ID, TEST_INTERVIEW_ID, TEST_QUESTION_ID, null));

            assertEquals(ErrorCode.FAVORITE_LIMIT.getCode(), ex.getCode());
        }

        @Test
        @DisplayName("添加收藏 - 备注含特殊字符")
        void addFavorite_SpecialRemark() {
            when(favoriteMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
            when(favoriteMapper.insert(any(InterviewFavorite.class))).thenReturn(1);

            favoriteService.addFavorite(TEST_USER_ID, TEST_INTERVIEW_ID, TEST_QUESTION_ID,
                    "<b>系统设计</b> & 数据结构");

            verify(favoriteMapper).insert(argThat(fav ->
                    fav.getRemark().equals("<b>系统设计</b> & 数据结构")));
        }

        @Test
        @DisplayName("添加收藏 - 备注为 null")
        void addFavorite_NullRemark() {
            when(favoriteMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
            when(favoriteMapper.insert(any(InterviewFavorite.class))).thenReturn(1);

            favoriteService.addFavorite(TEST_USER_ID, TEST_INTERVIEW_ID, TEST_QUESTION_ID, null);

            verify(favoriteMapper).insert(argThat(fav -> fav.getRemark() == null));
        }

        @Test
        @DisplayName("取消收藏 - 验证逻辑删除")
        void removeFavorite_VerifyDelete() {
            when(favoriteMapper.selectById(TEST_FAVORITE_ID)).thenReturn(testFavorite);
            when(favoriteMapper.deleteById(TEST_FAVORITE_ID)).thenReturn(1);

            favoriteService.removeFavorite(TEST_FAVORITE_ID, TEST_USER_ID);

            verify(favoriteMapper).deleteById(TEST_FAVORITE_ID);
        }

        @Test
        @DisplayName("同一用户收藏不同问题")
        void addFavorite_DifferentQuestions() {
            when(favoriteMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
            when(favoriteMapper.insert(any(InterviewFavorite.class))).thenReturn(1);

            favoriteService.addFavorite(TEST_USER_ID, TEST_INTERVIEW_ID, 1L, "问题1");
            favoriteService.addFavorite(TEST_USER_ID, TEST_INTERVIEW_ID, 2L, "问题2");

            verify(favoriteMapper, times(2)).insert(any(InterviewFavorite.class));
        }

        @Test
        @DisplayName("不同用户收藏同一问题")
        void addFavorite_DifferentUsers() {
            when(favoriteMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
            when(favoriteMapper.insert(any(InterviewFavorite.class))).thenReturn(1);

            favoriteService.addFavorite(1L, TEST_INTERVIEW_ID, TEST_QUESTION_ID, null);
            favoriteService.addFavorite(2L, TEST_INTERVIEW_ID, TEST_QUESTION_ID, null);

            verify(favoriteMapper, times(2)).insert(any(InterviewFavorite.class));
        }
    }
}
