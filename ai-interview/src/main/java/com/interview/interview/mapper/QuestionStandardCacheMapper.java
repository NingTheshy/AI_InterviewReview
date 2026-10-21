package com.interview.interview.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.interview.interview.entity.QuestionStandardCache;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface QuestionStandardCacheMapper extends BaseMapper<QuestionStandardCache> {
    @Delete("DELETE FROM question_standard_cache WHERE scope_type='PERSONAL' AND interview_id=#{interviewId}")
    int deletePersonalByInterview(@Param("interviewId") Long interviewId);
}
