package com.interview.interview.service;

/**
 * 面试资源访问校验服务。
 *
 * <p>功能权限只表示用户可以执行某类操作；本服务继续校验具体面试和问题是否属于当前用户，
 * 或是否已通过公开面经对当前用户可见。</p>
 */
public interface InterviewResourceAccessService {

    /** 校验面试存在且属于当前用户。 */
    void requireOwnedInterview(Long interviewId, Long userId);

    /** 校验问题属于指定面试，且该面试属于当前用户。 */
    void requireOwnedQuestion(Long interviewId, Long questionId, Long userId);

    /** 校验面试属于当前用户，或存在仍有效的公开面经。 */
    void requireVisibleInterview(Long interviewId, Long userId);

    /** 校验问题属于指定面试，且该面试对当前用户可见。 */
    void requireVisibleQuestion(Long interviewId, Long questionId, Long userId);
}
