package com.interview.interview.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.interview.interview.entity.InterviewNote;
import org.apache.ibatis.annotations.Mapper;

/**
 * 面试笔记 Mapper 接口
 * <p>
 * 继承 MyBatis-Plus {@link BaseMapper}，提供 interview_note 表的基本 CRUD 操作。
 * 自定义查询通过 {@link com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper} 构建。
 * </p>
 */
@Mapper
public interface InterviewNoteMapper extends BaseMapper<InterviewNote> {
}
