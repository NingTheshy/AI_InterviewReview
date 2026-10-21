package com.interview.interview.service;

import com.interview.ai.service.ScoringService;
import com.interview.interview.entity.Interview;

import java.util.List;

/** 标准答案与评分量表的隔离缓存。 */
public interface QuestionStandardCacheService {
    List<ScoringService.SavedRubric> load(Interview interview);
    void save(Interview interview, String scoreResultJson);
}
