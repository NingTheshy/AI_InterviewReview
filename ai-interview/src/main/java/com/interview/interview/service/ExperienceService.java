package com.interview.interview.service;

import com.interview.common.result.PageResult;
import com.interview.interview.dto.ExperienceDetailResponse;
import com.interview.interview.dto.ExperienceListResponse;
import com.interview.interview.dto.ExperiencePublicationResponse;
import com.interview.interview.dto.ExperiencePublicationRequest;
import com.interview.interview.dto.ExperienceEditorResponse;

/**
 * 面经服务接口
 * <p>
 * 提供公开面经广场的浏览功能，包括列表查询和详情查看。
 * 面经来源于用户主动公开的分享记录。
 * </p>
 */
public interface ExperienceService {

    /**
     * 分页查询公开面经列表
     * <p>
     * 仅返回公开且未过期的面经。支持按公司、职位、行业筛选，
     * 以及按发布时间、浏览量或面试总分排序。
     * </p>
     *
     * @param page          页码
     * @param size          每页大小
     * @param companyName   公司名称筛选
     * @param positionTitle 职位筛选
     * @param industry      行业筛选
     * @param sortBy        排序方式：publishedAt、viewCount、overallScore；默认按发布时间
     * @return 面经列表（分页）
     */
    PageResult<ExperienceListResponse> listPublic(int page, int size,
                                                  String keyword, String positionTitle, String sortBy);

    /**
     * 获取面经详情
     * <p>
     * 通过分享 Token 获取面经详情，自动增加浏览量。
     * 不包含音频文件、转写文本和个人笔记（隐私保护）。
     * </p>
     *
     * @param token 分享 Token
     * @return 面经详情
     * @throws com.interview.common.exception.BusinessException 面经不存在或已过期
     */
    ExperienceDetailResponse getDetail(Long id, Long viewerId);
    ExperiencePublicationResponse publish(Long interviewId, Long userId, ExperiencePublicationRequest request);
    ExperiencePublicationResponse getPublication(Long interviewId, Long userId);
    ExperienceEditorResponse editor(Long interviewId, Long userId);
    ExperienceEditorResponse editPublished(Long experienceId, Long userId);
    ExperiencePublicationResponse updatePublication(Long experienceId, Long userId, ExperiencePublicationRequest request);
    void deletePublication(Long experienceId, Long userId);
    PageResult<ExperiencePublicationResponse> mine(Long userId, int page, int size);
    PageResult<ExperienceListResponse> favorites(Long userId, int page, int size);
    void like(Long experienceId, Long userId, boolean enabled);
    void favorite(Long experienceId, Long userId, boolean enabled);
}
