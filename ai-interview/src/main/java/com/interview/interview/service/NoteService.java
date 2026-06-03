package com.interview.interview.service;

/**
 * 笔记服务接口
 * <p>
 * 提供面试笔记和问题笔记的读写功能。
 * 笔记类型通过 {@link com.interview.common.constant.NoteType} 区分。
 * </p>
 */
public interface NoteService {

    /**
     * 获取面试笔记
     *
     * @param interviewId 面试 ID
     * @param userId      用户 ID
     * @return 笔记内容，不存在时返回空字符串
     */
    String getInterviewNote(Long interviewId, Long userId);

    /**
     * 保存面试笔记（新建或更新）
     *
     * @param interviewId 面试 ID
     * @param userId      用户 ID
     * @param content     笔记内容
     */
    void saveInterviewNote(Long interviewId, Long userId, String content);

    /**
     * 获取问题笔记
     *
     * @param interviewId 面试 ID
     * @param questionId  问题 ID
     * @param userId      用户 ID
     * @return 笔记内容，不存在时返回空字符串
     */
    String getQuestionNote(Long interviewId, Long questionId, Long userId);

    /**
     * 保存问题笔记（新建或更新）
     *
     * @param interviewId 面试 ID
     * @param questionId  问题 ID
     * @param userId      用户 ID
     * @param content     笔记内容
     */
    void saveQuestionNote(Long interviewId, Long questionId, Long userId, String content);
}
