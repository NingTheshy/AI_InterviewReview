package com.interview.interview.service.impl;

import com.interview.common.constant.ErrorCode;
import com.interview.common.exception.BusinessException;
import com.interview.interview.entity.InterviewComment;
import com.interview.interview.mapper.InterviewCommentMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** 事务内删除一条评论及全部层级的回复。 */
@Service
@RequiredArgsConstructor
public class CommentDeletionService {
    private final InterviewCommentMapper commentMapper;

    @Transactional
    public void deleteThread(InterviewComment root) {
        if (root == null || Integer.valueOf(1).equals(root.getDeleted())) {
            throw new BusinessException(ErrorCode.NOT_FOUND.getCode(), ErrorCode.NOT_FOUND.getMessage());
        }
        commentMapper.lockExperience(root.getExperienceId());
        InterviewComment current = commentMapper.selectById(root.getId());
        if (current == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND.getCode(), ErrorCode.NOT_FOUND.getMessage());
        }
        List<Long> ids = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        List<Long> frontier = List.of(root.getId());
        while (!frontier.isEmpty()) {
            List<Long> next = new ArrayList<>();
            for (Long id : frontier) {
                if (seen.add(id)) ids.add(id);
            }
            for (int offset = 0; offset < frontier.size(); offset += 500) {
                List<Long> children = commentMapper.selectChildIds(frontier.subList(offset, Math.min(offset + 500, frontier.size())));
                for (Long child : children) if (!seen.contains(child)) next.add(child);
            }
            frontier = next;
        }
        for (int offset = 0; offset < ids.size(); offset += 500) {
            int updated = commentMapper.softDeleteIds(ids.subList(offset, Math.min(offset + 500, ids.size())));
            if (offset == 0 && updated == 0) {
                throw new BusinessException(ErrorCode.NOT_FOUND.getCode(), ErrorCode.NOT_FOUND.getMessage());
            }
        }
    }
}
