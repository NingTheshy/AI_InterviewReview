package com.interview.interview.service;

import com.interview.common.result.PageResult;
import com.interview.interview.dto.InterviewDetailResponse;
import com.interview.interview.dto.InterviewListResponse;
import com.interview.interview.dto.InterviewStatusResponse;
import org.springframework.web.multipart.MultipartFile;

/**
 * 面试服务接口
 * <p>
 * 提供面试记录的核心业务操作，包括上传、查询、删除和重试处理。
 * </p>
 */
public interface InterviewService {

    /**
     * 上传面试记录
     * <p>
     * 保存音频文件、简历文件和 JD 信息，创建面试记录，
     * 并触发异步 AI 处理流程。
     * </p>
     *
     * @param userId        用户 ID
     * @param audioFile     音频文件（必填）
     * @param resumeFile    简历文件（可选）
     * @param jdText        职位描述文本（必填）
     * @param title         面试标题
     * @param companyName   公司名称
     * @param positionTitle 职位名称
     * @param industry      行业
     * @param interviewType 面试类型（coding/behavioral/system_design/comprehensive）
     * @return 新建面试记录的 ID
     */
    Long upload(Long userId, MultipartFile audioFile, MultipartFile resumeFile,
                String jdText, String title, String companyName,
                String positionTitle, String industry, String interviewType);

    /**
     * 分页查询当前用户的面试记录
     *
     * @param userId        用户 ID
     * @param page          页码（从 1 开始）
     * @param size          每页大小
     * @param companyName   公司名称筛选（模糊匹配）
     * @param positionTitle 职位筛选（模糊匹配）
     * @param industry      行业筛选（精确匹配）
     * @param interviewType 面试类型筛选（精确匹配）
     * @return 面试记录分页结果
     */
    PageResult<InterviewListResponse> list(Long userId, int page, int size,
                                           String companyName, String positionTitle,
                                           String industry, String interviewType,
                                           String sortBy);

    /**
     * 获取面试详情（仅限记录所有者）
     *
     * @param id     面试 ID
     * @param userId 当前用户 ID（用于权限校验）
     * @return 面试详情（含问题列表和评分）
     * @throws com.interview.common.exception.BusinessException 面试不存在或无权限
     */
    InterviewDetailResponse getDetail(Long id, Long userId);

    /**
     * 删除面试记录（软删除，仅限记录所有者）
     *
     * @param id     面试 ID
     * @param userId 当前用户 ID
     * @throws com.interview.common.exception.BusinessException 面试不存在或无权限
     */
    void delete(Long id, Long userId);

    /**
     * 查询面试处理进度（仅限记录所有者）
     *
     * @param id     面试 ID
     * @param userId 当前用户 ID（用于权限校验）
     * @return 面试处理进度信息
     * @throws com.interview.common.exception.BusinessException 面试不存在或无权限
     */
    InterviewStatusResponse getStatus(Long id, Long userId);

    /**
     * 重新处理面试（支持失败重试和手动重新评分）
     * <p>
     * 重置面试状态为处理中，重新触发异步 AI 处理流程（从语音转文字开始全流程重跑）。
     * </p>
     * <p>
     * 支持场景：
     * <ul>
     *   <li>失败重试：处理失败后修复问题重新处理</li>
     *   <li>手动重新评分：已完成的面试用更新后的评分策略重新评估</li>
     * </ul>
     * </p>
     * <p>
     * 限制：处理中的记录不允许重试（防重复提交）。
     * </p>
     *
     * @param id     面试 ID
     * @param userId 当前用户 ID
     * @throws com.interview.common.exception.BusinessException 面试不存在、无权限或正在处理中
     */
    void retry(Long id, Long userId);
}
