package com.interview.interview.service.impl;

import com.interview.interview.mapper.InterviewCommentMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 物理清理七天前删除的评论和回复。 */
@Service
@RequiredArgsConstructor
public class CommentCleanupService {

    private final InterviewCommentMapper commentMapper;

    @Transactional
    public void purgeExpiredComments() {
        commentMapper.purgeExpiredDeleted();
    }
}
