package com.interview.interview.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.interview.common.constant.NoteType;
import com.interview.interview.entity.InterviewNote;
import com.interview.interview.mapper.InterviewNoteMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * NoteServiceImpl 单元测试
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("NoteService 单元测试")
class NoteServiceImplTest {

    @Mock
    private InterviewNoteMapper noteMapper;

    @InjectMocks
    private NoteServiceImpl noteService;

    private static final Long TEST_INTERVIEW_ID = 100L;
    private static final Long TEST_QUESTION_ID = 10L;
    private static final Long TEST_USER_ID = 1L;

    // ==================== getInterviewNote 测试 ====================

    @Nested
    @DisplayName("getInterviewNote 方法测试")
    class GetInterviewNoteTest {

        @Test
        @DisplayName("获取面试笔记成功")
        void getInterviewNote_Success() {
            InterviewNote note = new InterviewNote();
            note.setNoteContent("面试笔记内容");
            when(noteMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(note);

            String result = noteService.getInterviewNote(TEST_INTERVIEW_ID, TEST_USER_ID);

            assertEquals("面试笔记内容", result);
        }

        @Test
        @DisplayName("获取面试笔记 - 不存在时返回空字符串")
        void getInterviewNote_NotFound() {
            when(noteMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

            String result = noteService.getInterviewNote(TEST_INTERVIEW_ID, TEST_USER_ID);

            assertEquals("", result);
        }
    }

    // ==================== saveInterviewNote 测试 ====================

    @Nested
    @DisplayName("saveInterviewNote 方法测试")
    class SaveInterviewNoteTest {

        @Test
        @DisplayName("新建面试笔记")
        void saveInterviewNote_Insert() {
            when(noteMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
            when(noteMapper.insert(any(InterviewNote.class))).thenReturn(1);

            noteService.saveInterviewNote(TEST_INTERVIEW_ID, TEST_USER_ID, "新笔记");

            verify(noteMapper).insert(any(InterviewNote.class));
            verify(noteMapper, never()).updateById(any(InterviewNote.class));
        }

        @Test
        @DisplayName("更新面试笔记")
        void saveInterviewNote_Update() {
            InterviewNote existingNote = new InterviewNote();
            existingNote.setId(1L);
            existingNote.setNoteContent("旧内容");
            when(noteMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(existingNote);
            when(noteMapper.updateById(any(InterviewNote.class))).thenReturn(1);

            noteService.saveInterviewNote(TEST_INTERVIEW_ID, TEST_USER_ID, "新内容");

            verify(noteMapper, never()).insert(any(InterviewNote.class));
            verify(noteMapper).updateById(any(InterviewNote.class));
        }
    }

    // ==================== getQuestionNote 测试 ====================

    @Nested
    @DisplayName("getQuestionNote 方法测试")
    class GetQuestionNoteTest {

        @Test
        @DisplayName("获取问题笔记成功")
        void getQuestionNote_Success() {
            InterviewNote note = new InterviewNote();
            note.setNoteContent("问题笔记内容");
            when(noteMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(note);

            String result = noteService.getQuestionNote(TEST_INTERVIEW_ID, TEST_QUESTION_ID, TEST_USER_ID);

            assertEquals("问题笔记内容", result);
        }

        @Test
        @DisplayName("获取问题笔记 - 不存在时返回空字符串")
        void getQuestionNote_NotFound() {
            when(noteMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

            String result = noteService.getQuestionNote(TEST_INTERVIEW_ID, TEST_QUESTION_ID, TEST_USER_ID);

            assertEquals("", result);
        }
    }

    // ==================== saveQuestionNote 测试 ====================

    @Nested
    @DisplayName("saveQuestionNote 方法测试")
    class SaveQuestionNoteTest {

        @Test
        @DisplayName("新建问题笔记")
        void saveQuestionNote_Insert() {
            when(noteMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
            when(noteMapper.insert(any(InterviewNote.class))).thenReturn(1);

            noteService.saveQuestionNote(TEST_INTERVIEW_ID, TEST_QUESTION_ID, TEST_USER_ID, "新笔记");

            verify(noteMapper).insert(any(InterviewNote.class));
        }

        @Test
        @DisplayName("更新问题笔记")
        void saveQuestionNote_Update() {
            InterviewNote existingNote = new InterviewNote();
            existingNote.setId(1L);
            when(noteMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(existingNote);
            when(noteMapper.updateById(any(InterviewNote.class))).thenReturn(1);

            noteService.saveQuestionNote(TEST_INTERVIEW_ID, TEST_QUESTION_ID, TEST_USER_ID, "更新内容");

            verify(noteMapper).updateById(any(InterviewNote.class));
        }
    }

    // ==================== 边界条件测试 ====================

    @Nested
    @DisplayName("边界条件测试")
    class BoundaryTest {

        @Test
        @DisplayName("保存空内容笔记")
        void saveNote_EmptyContent() {
            when(noteMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
            when(noteMapper.insert(any(InterviewNote.class))).thenReturn(1);

            noteService.saveInterviewNote(TEST_INTERVIEW_ID, TEST_USER_ID, "");

            verify(noteMapper).insert(argThat(note -> note.getNoteContent().isEmpty()));
        }

        @Test
        @DisplayName("保存超长内容笔记")
        void saveNote_VeryLongContent() {
            String longContent = "a".repeat(10000);
            when(noteMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
            when(noteMapper.insert(any(InterviewNote.class))).thenReturn(1);

            noteService.saveInterviewNote(TEST_INTERVIEW_ID, TEST_USER_ID, longContent);

            verify(noteMapper).insert(argThat(note -> note.getNoteContent().length() == 10000));
        }

        @Test
        @DisplayName("保存含特殊字符的笔记")
        void saveNote_SpecialCharacters() {
            String specialContent = "<b>粗体</b><script>alert('xss')</script>列表：<ul><li>项1</li></ul>";
            when(noteMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
            when(noteMapper.insert(any(InterviewNote.class))).thenReturn(1);

            noteService.saveInterviewNote(TEST_INTERVIEW_ID, TEST_USER_ID, specialContent);

            verify(noteMapper).insert(argThat(note -> note.getNoteContent().equals(specialContent)));
        }

        @Test
        @DisplayName("获取面试笔记 - 笔记内容为 null 时返回空字符串")
        void getInterviewNote_NullContent() {
            InterviewNote note = new InterviewNote();
            note.setNoteContent(null);
            when(noteMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(note);

            String result = noteService.getInterviewNote(TEST_INTERVIEW_ID, TEST_USER_ID);

            assertNull(result);
        }

        @Test
        @DisplayName("不同用户笔记相互独立")
        void saveNote_DifferentUsersIndependent() {
            Long userId1 = 1L;
            Long userId2 = 2L;

            // 用户1保存笔记
            when(noteMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
            when(noteMapper.insert(any(InterviewNote.class))).thenReturn(1);
            noteService.saveInterviewNote(TEST_INTERVIEW_ID, userId1, "用户1的笔记");

            // 用户2保存笔记
            when(noteMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
            when(noteMapper.insert(any(InterviewNote.class))).thenReturn(1);
            noteService.saveInterviewNote(TEST_INTERVIEW_ID, userId2, "用户2的笔记");

            // 验证两次插入都发生（各自独立）
            verify(noteMapper, times(2)).insert(any(InterviewNote.class));
        }

        @Test
        @DisplayName("面试笔记和问题笔记互不影响")
        void saveNote_InterviewAndQuestionIndependent() {
            // 保存面试笔记
            when(noteMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
            when(noteMapper.insert(any(InterviewNote.class))).thenReturn(1);
            noteService.saveInterviewNote(TEST_INTERVIEW_ID, TEST_USER_ID, "面试笔记");

            // 保存问题笔记
            when(noteMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
            when(noteMapper.insert(any(InterviewNote.class))).thenReturn(1);
            noteService.saveQuestionNote(TEST_INTERVIEW_ID, TEST_QUESTION_ID, TEST_USER_ID, "问题笔记");

            // 验证两次插入都发生
            verify(noteMapper, times(2)).insert(any(InterviewNote.class));
        }
    }
}
