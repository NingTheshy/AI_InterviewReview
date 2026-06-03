package com.interview.interview.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.interview.common.constant.NoteType;
import com.interview.interview.entity.InterviewNote;
import com.interview.interview.mapper.InterviewNoteMapper;
import com.interview.interview.service.NoteService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 笔记服务实现类
 * <p>
 * 提供面试笔记和问题笔记的读写功能。采用 "查询-判断-新建/更新" 模式：
 * 如果笔记已存在则更新，否则新建。
 * </p>
 * <p>
 * 笔记类型通过 {@link NoteType} 枚举区分：
 * <ul>
 *   <li>INTERVIEW - 面试整体笔记</li>
 *   <li>QUESTION - 具体问题笔记</li>
 * </ul>
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NoteServiceImpl implements NoteService {

    private final InterviewNoteMapper noteMapper;

    /**
     * {@inheritDoc}
     * <p>
     * 查询条件：面试 ID + 用户 ID + 笔记类型 = INTERVIEW
     * </p>
     */
    @Override
    public String getInterviewNote(Long interviewId, Long userId) {
        LambdaQueryWrapper<InterviewNote> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(InterviewNote::getInterviewId, interviewId);
        wrapper.eq(InterviewNote::getUserId, userId);
        wrapper.eq(InterviewNote::getNoteType, NoteType.INTERVIEW.getCode());

        InterviewNote note = noteMapper.selectOne(wrapper);
        return note != null ? note.getNoteContent() : "";
    }

    /**
     * {@inheritDoc}
     * <p>
     * 实现逻辑：先查询是否存在，存在则更新，否则新建。
     * </p>
     */
    @Override
    public void saveInterviewNote(Long interviewId, Long userId, String content) {
        LambdaQueryWrapper<InterviewNote> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(InterviewNote::getInterviewId, interviewId);
        wrapper.eq(InterviewNote::getUserId, userId);
        wrapper.eq(InterviewNote::getNoteType, NoteType.INTERVIEW.getCode());

        InterviewNote note = noteMapper.selectOne(wrapper);

        if (note == null) {
            note = new InterviewNote();
            note.setInterviewId(interviewId);
            note.setUserId(userId);
            note.setNoteType(NoteType.INTERVIEW.getCode());
            note.setNoteContent(content);
            noteMapper.insert(note);
        } else {
            note.setNoteContent(content);
            noteMapper.updateById(note);
        }

        log.info("保存面试笔记: interviewId={}, userId={}", interviewId, userId);
    }

    /**
     * {@inheritDoc}
     * <p>
     * 查询条件：面试 ID + 问题 ID + 用户 ID + 笔记类型 = QUESTION
     * </p>
     */
    @Override
    public String getQuestionNote(Long interviewId, Long questionId, Long userId) {
        LambdaQueryWrapper<InterviewNote> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(InterviewNote::getInterviewId, interviewId);
        wrapper.eq(InterviewNote::getQuestionId, questionId);
        wrapper.eq(InterviewNote::getUserId, userId);
        wrapper.eq(InterviewNote::getNoteType, NoteType.QUESTION.getCode());

        InterviewNote note = noteMapper.selectOne(wrapper);
        return note != null ? note.getNoteContent() : "";
    }

    /**
     * {@inheritDoc}
     * <p>
     * 实现逻辑：先查询是否存在，存在则更新，否则新建。
     * </p>
     */
    @Override
    public void saveQuestionNote(Long interviewId, Long questionId, Long userId, String content) {
        LambdaQueryWrapper<InterviewNote> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(InterviewNote::getInterviewId, interviewId);
        wrapper.eq(InterviewNote::getQuestionId, questionId);
        wrapper.eq(InterviewNote::getUserId, userId);
        wrapper.eq(InterviewNote::getNoteType, NoteType.QUESTION.getCode());

        InterviewNote note = noteMapper.selectOne(wrapper);

        if (note == null) {
            note = new InterviewNote();
            note.setInterviewId(interviewId);
            note.setQuestionId(questionId);
            note.setUserId(userId);
            note.setNoteType(NoteType.QUESTION.getCode());
            note.setNoteContent(content);
            noteMapper.insert(note);
        } else {
            note.setNoteContent(content);
            noteMapper.updateById(note);
        }

        log.info("保存问题笔记: interviewId={}, questionId={}, userId={}", interviewId, questionId, userId);
    }
}
