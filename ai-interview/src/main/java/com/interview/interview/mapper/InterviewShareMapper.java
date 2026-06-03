package com.interview.interview.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.interview.interview.entity.InterviewShare;
import org.apache.ibatis.annotations.Mapper;

/**
 * 面试分享 Mapper 接口
 * <p>
 * 继承 MyBatis-Plus {@link BaseMapper}，提供 interview_share 表的基本 CRUD 操作。
 * 支持逻辑删除，查询时自动过滤已删除记录。
 * </p>
 */
@Mapper
public interface InterviewShareMapper extends BaseMapper<InterviewShare> {
}
