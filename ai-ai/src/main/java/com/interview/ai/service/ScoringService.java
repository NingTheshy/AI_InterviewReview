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

    /**
     * 双阶段分批评估（高精度模式）
     * <p>
     * 第一阶段：纯分析 — 提取每个维度的观察、证据和层级分类（不打分）
     * 第二阶段：评分 — 基于层级分类映射到精确分数区间
     * </p>
     * <p>
     * 优势：分析与评分分离，层级分类比直接打分更稳定
     * 成本：约为普通分批评估的 2 倍 LLM 调用
     * </p>
     *
     * @param text        面试对话文本
     * @param jdText      岗位 JD
     * @param resumeText  简历文本
     * @param configId    AI 配置 ID（null 使用默认）
     * @param companyTier 公司档次代码（1-5）
     * @return JSON 格式的汇总评分结果
     */
    String analyzeAndScoreBatchTwoPhase(String text, String jdText, String resumeText, Long configId, Integer companyTier);
}
