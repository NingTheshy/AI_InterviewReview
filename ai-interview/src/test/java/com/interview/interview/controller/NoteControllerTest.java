package com.interview.interview.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.interview.common.exception.GlobalExceptionHandler;
import com.interview.interview.dto.NoteRequest;
import com.interview.interview.service.NoteService;
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
 * NoteController 功能测试
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("NoteController 功能测试")
class NoteControllerTest {

    private MockMvc mockMvc;
    private ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private NoteService noteService;

    @InjectMocks
    private NoteController noteController;

    private static final Long TEST_USER_ID = 1L;
    private static final Long TEST_INTERVIEW_ID = 100L;
    private static final Long TEST_QUESTION_ID = 10L;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(noteController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(TEST_USER_ID, "testuser",
                        List.of(new SimpleGrantedAuthority("ROLE_USER"))));
    }

    // ==================== getInterviewNote 测试 ====================

    @Nested
    @DisplayName("GET /interviews/{id}/note 测试")
    class GetInterviewNoteTest {

        @Test
        @DisplayName("获取面试笔记成功")
        void getInterviewNote_Success() throws Exception {
            when(noteService.getInterviewNote(anyLong(), anyLong())).thenReturn("笔记内容");

            mockMvc.perform(get("/interviews/{id}/note", TEST_INTERVIEW_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content").value("笔记内容"));
        }

        @Test
        @DisplayName("获取面试笔记 - 不存在时返回空字符串")
        void getInterviewNote_Empty() throws Exception {
            when(noteService.getInterviewNote(anyLong(), anyLong())).thenReturn("");

            mockMvc.perform(get("/interviews/{id}/note", TEST_INTERVIEW_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content").value(""));
        }
    }

    // ==================== saveInterviewNote 测试 ====================

    @Nested
    @DisplayName("PUT /interviews/{id}/note 测试")
    class SaveInterviewNoteTest {

        @Test
        @DisplayName("保存面试笔记成功")
        void saveInterviewNote_Success() throws Exception {
            NoteRequest request = new NoteRequest();
            request.setContent("新的笔记内容");
            doNothing().when(noteService).saveInterviewNote(anyLong(), anyLong(), anyString());

            mockMvc.perform(put("/interviews/{id}/note", TEST_INTERVIEW_ID)
                            .contentType("application/json")
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));
        }
    }

    // ==================== getQuestionNote 测试 ====================

    @Nested
    @DisplayName("GET /interviews/{id}/questions/{qid}/note 测试")
    class GetQuestionNoteTest {

        @Test
        @DisplayName("获取问题笔记成功")
        void getQuestionNote_Success() throws Exception {
            when(noteService.getQuestionNote(anyLong(), anyLong(), anyLong())).thenReturn("问题笔记");

            mockMvc.perform(get("/interviews/{id}/questions/{qid}/note",
                            TEST_INTERVIEW_ID, TEST_QUESTION_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content").value("问题笔记"));
        }
    }

    // ==================== saveQuestionNote 测试 ====================

    @Nested
    @DisplayName("PUT /interviews/{id}/questions/{qid}/note 测试")
    class SaveQuestionNoteTest {

        @Test
        @DisplayName("保存问题笔记成功")
        void saveQuestionNote_Success() throws Exception {
            NoteRequest request = new NoteRequest();
            request.setContent("问题笔记内容");
            doNothing().when(noteService).saveQuestionNote(anyLong(), anyLong(), anyLong(), anyString());

            mockMvc.perform(put("/interviews/{id}/questions/{qid}/note",
                            TEST_INTERVIEW_ID, TEST_QUESTION_ID)
                            .contentType("application/json")
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));
        }
    }

    // ==================== 边界条件测试 ====================

    @Nested
    @DisplayName("边界条件测试")
    class BoundaryTest {

        @Test
        @DisplayName("保存面试笔记 - 内容为空字符串")
        void saveInterviewNote_EmptyContent() throws Exception {
            NoteRequest request = new NoteRequest();
            request.setContent("");
            doNothing().when(noteService).saveInterviewNote(anyLong(), anyLong(), anyString());

            mockMvc.perform(put("/interviews/{id}/note", TEST_INTERVIEW_ID)
                            .contentType("application/json")
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));
        }

        @Test
        @DisplayName("保存面试笔记 - 请求体为空 JSON")
        void saveInterviewNote_EmptyBody() throws Exception {
            mockMvc.perform(put("/interviews/{id}/note", TEST_INTERVIEW_ID)
                            .contentType("application/json")
                            .content("{}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));
        }

        @Test
        @DisplayName("保存问题笔记 - 内容为空字符串")
        void saveQuestionNote_EmptyContent() throws Exception {
            NoteRequest request = new NoteRequest();
            request.setContent("");
            doNothing().when(noteService).saveQuestionNote(anyLong(), anyLong(), anyLong(), anyString());

            mockMvc.perform(put("/interviews/{id}/questions/{qid}/note",
                            TEST_INTERVIEW_ID, TEST_QUESTION_ID)
                            .contentType("application/json")
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));
        }

        @Test
        @DisplayName("保存笔记 - 超长内容")
        void saveNote_VeryLongContent() throws Exception {
            NoteRequest request = new NoteRequest();
            request.setContent("a".repeat(10001));

            mockMvc.perform(put("/interviews/{id}/note", TEST_INTERVIEW_ID)
                            .contentType("application/json")
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("获取笔记 - 不同用户返回不同内容")
        void getNote_DifferentUsersReturnDifferentContent() throws Exception {
            when(noteService.getInterviewNote(eq(TEST_INTERVIEW_ID), eq(TEST_USER_ID)))
                    .thenReturn("用户A的笔记");

            mockMvc.perform(get("/interviews/{id}/note", TEST_INTERVIEW_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content").value("用户A的笔记"));
        }
    }
}
