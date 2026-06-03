package com.interview.ai.service;

/**
 * AI 评分服务接口
 * <p>
 * 提供面试文本的 AI 评估功能，支持单次评估和分批评估两种模式。
 * 分批评估将长文本拆分为多个批次独立评估，再合并汇总，提高稳定性。
 * </p>
 */
public interface ScoringService {

    /**
     * 单次评估：一次性返回所有问题的评分结果
     *
     * @param text     面试对话文本
     * @param jdText   岗位 JD
     * @param resumeText 简历文本
     * @param configId AI 配置 ID（null 使用默认）
     * @return JSON 格式的评分结果
     */
    String analyzeAndScore(String text, String jdText, String resumeText, Long configId);

    /**
     * 单次评估（带公司档次）
     *
     * @param text        面试对话文本
     * @param jdText      岗位 JD
     * @param resumeText  简历文本
     * @param configId    AI 配置 ID（null 使用默认）
     * @param companyTier 公司档次代码（1-5）
     * @return JSON 格式的评分结果
     */
    String analyzeAndScore(String text, String jdText, String resumeText, Long configId, Integer companyTier);

    /**
     * 分批评估：将长文本拆分为多个批次独立评估，再合并汇总
     *
     * @param text        面试对话文本
     * @param jdText      岗位 JD
     * @param resumeText  简历文本
     * @param configId    AI 配置 ID（null 使用默认）
     * @param companyTier 公司档次代码（1-5）
     * @return JSON 格式的汇总评分结果
     */
    String analyzeAndScoreBatch(String text, String jdText, String resumeText, Long configId, Integer companyTier);
}
