package com.interview.interview.service;

/**
 * 面试资源访问校验服务。
 *
 * <p>功能权限只表示用户可以执行某类操作；本服务继续校验具体面试和问题是否属于当前用户。
 * 公开面经仅通过独立题目快照提供内容，不开放原面试资源。</p>
 */
public interface InterviewResourceAccessService {

    /** 校验面试存在且属于当前用户。 */
    void requireOwnedInterview(Long interviewId, Long userId);

    /** 校验问题属于指定面试，且该面试属于当前用户。 */
    void requireOwnedQuestion(Long interviewId, Long questionId, Long userId);

    /** 校验面试属于当前用户，供私人题目收藏等旧调用点使用。 */
    void requireVisibleInterview(Long interviewId, Long userId);

    /** 校验问题属于指定面试，且该面试属于当前用户。 */
    void requireVisibleQuestion(Long interviewId, Long questionId, Long userId);
}
