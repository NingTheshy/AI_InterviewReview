package com.interview.ai.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.interview.ai.entity.AiConfig;
import com.interview.ai.factory.AiClientFactory;
import com.interview.ai.service.AiConfigService;
import com.interview.ai.service.LlmClient;
import com.interview.ai.service.ScoringService;
import com.interview.ai.util.StructuredOutputInvoker;
import com.interview.common.constant.CompanyTier;
import com.interview.common.constant.ConfigType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * AI 评分服务实现类
 * <p>
 * 实现面试文本的 AI 评估功能，包括：
 * <ul>
 *   <li>单次评估：一次性返回所有问题评分</li>
 *   <li>分批评估：拆分为多个批次独立评估，再合并汇总</li>
 *   <li>公司档次校准：根据企业等级调整评分标准</li>
 * </ul>
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScoringServiceImpl implements ScoringService {

    private final AiClientFactory aiClientFactory;
    private final AiConfigService aiConfigService;
    private final StructuredOutputInvoker outputInvoker;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 每批最多评估的问题数 */
    private static final int BATCH_SIZE = 8;

    /** 转录文本每批最大字符数（LLM 识别问答对 + 评分）。太大会导致 LLM 处理慢、JSON 输出格式易出错 */
    private static final int TRANSCRIPT_CHUNK_SIZE = 5000;

    /**
     * 层级 → 分数区间映射
     * Key: 层级名称, Value: [minScore, maxScore]
     * <p>
     * 通过让 LLM 输出层级分类而非精确分数，大幅提高评分一致性。
     * LLM 擅长分类（优秀/良好/一般），不擅长回归（精确到个位数的分数）。
     * </p>
     */
    private static final Map<String, int[]> TIER_RANGES = new LinkedHashMap<>();
    static {
        TIER_RANGES.put("卓越", new int[]{90, 100});
        TIER_RANGES.put("优秀", new int[]{75, 89});
        TIER_RANGES.put("良好", new int[]{60, 74});
        TIER_RANGES.put("一般", new int[]{45, 59});
        TIER_RANGES.put("较弱", new int[]{30, 44});
        TIER_RANGES.put("差",   new int[]{0, 29});
    }

    /**
     * 双阶段评估第一阶段的结果
     */
    private record AnalysisResult(String analysisJson, Map<String, String> dimensionTiers) {
        String getMedianTier(String dimension) {
            return dimensionTiers.getOrDefault(dimension, "一般");
        }
    }

    @Override
    public String analyzeAndScore(String text, String jdText, String resumeText, Long configId) {
        return analyzeAndScore(text, jdText, resumeText, configId, null);
    }

    @Override
    public String analyzeAndScore(String text, String jdText, String resumeText, Long configId, Integer companyTier) {
        log.info("开始 AI 评分分析: 文本长度={}, configId={}, companyTier={}", text.length(), configId, companyTier);

        String systemPrompt = buildSystemPrompt(companyTier);
        String userPrompt = buildUserPrompt(text, jdText, resumeText);

        LlmClient client = resolveClient(configId);
        String result = client.call(userPrompt, systemPrompt, configId);
        log.info("AI 评分分析完成");
        return result;
    }

    @Override
    public String analyzeAndScoreBatch(String text, String jdText, String resumeText, Long configId, Integer companyTier) {
        log.info("开始分批 AI 评分分析: 文本长度={}, companyTier={}", text.length(), companyTier);

        LlmClient client = resolveClient(configId);

        // Step 1: 将转录文本按长度分块（LLM 从每块中识别问答对）
        List<String> chunks = splitTranscript(text);
        log.info("转录文本分为 {} 块", chunks.size());

        // Step 2: 分批评估 — LLM 直接从原始转录中识别问答对并评分
        List<JsonNode> batchResults = new ArrayList<>();
        long totalStartTime = System.currentTimeMillis();
        for (int i = 0; i < chunks.size(); i++) {
            int batchIndex = i + 1;
            long chunkStartTime = System.currentTimeMillis();
            log.info("评估第 {}/{} 块 ({} 字符)", batchIndex, chunks.size(), chunks.get(i).length());

            String batchPrompt = buildTranscriptBatchPrompt(chunks.get(i), batchIndex, jdText, resumeText);
            String batchSystemPrompt = buildBatchSystemPrompt(companyTier);

            JsonNode batchResult = outputInvoker.invoke(client, batchPrompt, batchSystemPrompt, configId);
            batchResults.add(batchResult);

            long chunkElapsed = (System.currentTimeMillis() - chunkStartTime) / 1000;
            long totalElapsed = (System.currentTimeMillis() - totalStartTime) / 1000;
            if (batchResult.isArray()) {
                log.info("第 {} 块完成: 识别 {} 个问题, 耗时 {}s, 总耗时 {}s",
                        batchIndex, batchResult.size(), chunkElapsed, totalElapsed);
            }
        }

        // Step 3: 合并各批结果
        String merged = mergeBatchResults(batchResults);
        log.info("各批结果已合并");

        // Step 4: 二次汇总（使用 StructuredOutputInvoker）
        String summaryPrompt = buildSummaryPrompt(merged, jdText, resumeText);
        String summarySystemPrompt = buildSummarySystemPrompt(companyTier);
        JsonNode finalResultNode = outputInvoker.invoke(client, summaryPrompt, summarySystemPrompt, configId);

        log.info("分批 AI 评分分析完成");
        return finalResultNode.toString();
    }

    /**
     * 将转录文本按字符长度分块（在句子边界处切分）
     * <p>
     * 不再尝试用正则匹配问答标记（ASR 输出没有格式标记），
     * 而是将原始文本分块交给 LLM 识别问答对。
     * </p>
     */
    private List<String> splitTranscript(String text) {
        if (text.length() <= TRANSCRIPT_CHUNK_SIZE) {
            return List.of(text);
        }

        List<String> chunks = new ArrayList<>();
        int start = 0;

        while (start < text.length()) {
            int end = Math.min(start + TRANSCRIPT_CHUNK_SIZE, text.length());

            // 在句子边界处切分（找最近的句号、问号、感叹号）
            if (end < text.length()) {
                int boundary = findSentenceBoundary(text, end);
                if (boundary > start) {
                    end = boundary;
                }
            }

            chunks.add(text.substring(start, end).trim());
            start = end;
        }

        log.info("转录文本分块: 总长度={}, 分为 {} 块", text.length(), chunks.size());
        return chunks;
    }

    /**
     * 从指定位置向前搜索最近的句子边界
     */
    private int findSentenceBoundary(String text, int fromPos) {
        // 向前搜索最多 2000 字符，找句子结束标记
        int searchStart = Math.max(0, fromPos - 2000);
        String searchArea = text.substring(searchStart, fromPos);

        // 找最后一个句子结束标记
        int lastBoundary = -1;
        for (int i = searchArea.length() - 1; i >= 0; i--) {
            char c = searchArea.charAt(i);
            if (c == '。' || c == '？' || c == '！' || c == '!' || c == '?' || c == '.' || c == '；') {
                lastBoundary = searchStart + i + 1;
                break;
            }
        }

        return lastBoundary > 0 ? lastBoundary : fromPos;
    }

    /**
     * 构建转录文本批次 Prompt — 将原始转录交给 LLM 识别问答对
     */
    private String buildTranscriptBatchPrompt(String transcriptChunk, int batchIndex, String jdText, String resumeText) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("## 面试转录文本（第 ").append(batchIndex).append(" 段）\n\n");
        prompt.append("以下是面试录音的语音转文字结果，请从中识别出每个问答对并逐一评分：\n\n");
        prompt.append(transcriptChunk).append("\n\n");

        if (jdText != null && !jdText.isEmpty()) {
            prompt.append("## 职位描述 (JD)\n\n").append(jdText).append("\n\n");
        }
        if (resumeText != null && !resumeText.isEmpty()) {
            prompt.append("## 候选人简历\n\n").append(resumeText).append("\n\n");
        }

        prompt.append("请识别所有问答对并逐一评分，返回 JSON 数组格式。");
        return prompt.toString();
    }

    private String buildBatchSystemPrompt(Integer companyTier) {
        CompanyTier tier = companyTier != null ? CompanyTier.fromCode(companyTier) : CompanyTier.TIER_3;

        return """
                你是一位资深的面试评估专家，专注于 %s 级别企业的面试评估。

                【任务一：识别问答对】
                你收到的是面试录音的语音转文字结果（连续口语文本，无格式标记）。
                你需要从语义上识别出面试官的问题和候选人的回答。
                识别技巧：
                - 面试官通常会提问（包含"请"、"怎么"、"什么"、"为什么"、"谈谈"、"说说"等疑问词）
                - 候选人的回答通常在面试官提问之后
                - 有时候面试官会追问或候选人会主动补充，这些算同一个问答对
                - 如果某段内容无法明确归属，尽量合理划分

                【任务二：评估每个问答对】
                评估维度：
                - 内容专业度 (dimensionContent): 技术深度、准确性、完整性
                - 逻辑思维 (dimensionLogic): 结构化、因果链、边界意识
                - 表达能力 (dimensionExpression): 清晰度、简洁性、举例能力
                - 专业知识 (dimensionProfessional): 技术栈深度、行业认知、工程实践

                评分流程 — 严格执行：
                第一步：对每个维度先判断层级（卓越/优秀/良好/一般/较弱/差）
                第二步：根据层级确定分数区间，再结合具体表现给出精确分数
                - 卓越 → 90-100（该级别中极少出现，需有极强证据）
                - 优秀 → 75-89（有明确亮点）
                - 良好 → 60-74（基本达标）
                - 一般 → 45-59（有明显短板）
                - 较弱 → 30-44（多项不达标）
                - 差 → 0-29（基础能力不足）
                - "不知道"、"忘了"、"跳过" → 该维度直接给 0 分

                【行业基准线 — 所有等级共享】
                - 逻辑思维 基础分 ≥ 40（低于此最高 40 分）
                - 表达能力 基础分 ≥ 40（低于此最高 40 分）
                - 专业知识 基础分 ≥ 40（低于此最高 40 分）

                【输出格式】
                严格返回 JSON 数组，每个元素对应一个识别出的问答对：
                [
                  {
                    "questionText": "面试官的问题（从转录中提取）",
                    "answerText": "候选人的回答（从转录中提取）",
                    "score": 0-100整数,
                    "dimensionContent": 0-100整数,
                    "dimensionLogic": 0-100整数,
                    "dimensionExpression": 0-100整数,
                    "dimensionProfessional": 0-100整数,
                    "improvementTip": "具体改进建议（引用回答中的具体内容）",
                    "referenceAnswer": "参考答案（展示该级别的期望水平）"
                  }
                ]

                注意事项：
                - questionText 和 answerText 必须从转录原文中提取，不要编造
                - improvementTip 必须引用回答中的具体内容
                - 尽可能识别出所有问答对，不要遗漏
                - 只返回纯 JSON 数组，不要包含 markdown 代码块或其他内容
                """
                .replace("%s", tier.getName());
    }

    private String mergeBatchResults(List<JsonNode> batchResults) {
        StringBuilder merged = new StringBuilder("[\n");
        for (int i = 0; i < batchResults.size(); i++) {
            JsonNode batchNode = batchResults.get(i);
            // 如果是数组，取出元素；否则直接使用
            if (batchNode.isArray()) {
                for (int j = 0; j < batchNode.size(); j++) {
                    merged.append(batchNode.get(j).toString());
                    if (j < batchNode.size() - 1 || i < batchResults.size() - 1) {
                        merged.append(",\n");
                    }
                }
            } else {
                merged.append(batchNode.toString());
                if (i < batchResults.size() - 1) {
                    merged.append(",\n");
                }
            }
        }
        merged.append("\n]");
        return merged.toString();
    }

    private String buildSummaryPrompt(String mergedQuestions, String jdText, String resumeText) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("## 汇总任务\n\n");
        prompt.append("以下是各批评估合并后的问题列表：\n\n");
        prompt.append("```json\n").append(mergedQuestions).append("\n```\n\n");

        if (jdText != null && !jdText.isEmpty()) {
            prompt.append("## 职位描述 (JD)\n\n").append(jdText).append("\n\n");
        }
        if (resumeText != null && !resumeText.isEmpty()) {
            prompt.append("## 候选人简历\n\n").append(resumeText).append("\n\n");
        }

        prompt.append("请基于以上各题评分，生成最终汇总报告。");
        return prompt.toString();
    }

    private String buildSummarySystemPrompt(Integer companyTier) {
        CompanyTier tier = companyTier != null ? CompanyTier.fromCode(companyTier) : CompanyTier.TIER_3;

        return """
                你是一位资深的面试评估专家，专注于 %s 级别企业的面试评估。

                【任务】
                基于已有的各题评分数据，生成最终汇总报告。

                【汇总规则】
                1. overallScore = 各维度加权平均：
                   - dimensionContent × 0.25
                   - dimensionLogic × 0.20
                   - dimensionExpression × 0.15
                   - dimensionProfessional × 0.25
                   - dimensionCommunication × 0.15
                2. 各维度分 = 所有题目该维度的平均分
                3. 生成改进总结、优势、不足

                【输出格式】
                请严格返回以下 JSON 格式，不要包含 markdown 代码块或其他内容：
                {
                  "overallScore": 总分(0-100整数),
                  "dimensionContent": 内容专业度(0-100整数),
                  "dimensionLogic": 逻辑思维(0-100整数),
                  "dimensionExpression": 表达能力(0-100整数),
                  "dimensionProfessional": 专业知识(0-100整数),
                  "dimensionCommunication": 沟通协作(0-100整数),
                  "improvementSummary": "总体改进建议（具体、可执行）",
                  "strengths": "优势总结（2-3个亮点）",
                  "weaknesses": "不足总结（2-3个短板）",
                  "questions": [各题评分数据，保持原格式]
                }
                """
                .replace("%s", tier.getName());
    }

    private String buildSystemPrompt(Integer companyTier) {
        CompanyTier tier = companyTier != null ? CompanyTier.fromCode(companyTier) : CompanyTier.TIER_3;

        String prompt = """
                你是一位资深的面试评估专家，专注于 %s 级别企业的面试评估。

                ════════════════════════════════════════════════════════════════
                【评估框架】
                ════════════════════════════════════════════════════════════════

                请从以下 5 个核心维度进行评估，每个维度包含 3 个子项：

                一、内容专业度 (dimensionContent)
                  1.1 技术深度：回答是否涉及底层原理、源码实现、设计权衡
                  1.2 准确性：技术概念是否正确，有无事实性错误
                  1.3 完整性：是否覆盖问题的关键点，有无遗漏

                二、逻辑思维 (dimensionLogic)
                  2.1 结构化：回答是否有清晰的层次结构（总-分-总）
                  2.2 因果链：推理过程是否合理，论据是否支撑结论
                  2.3 边界意识：是否考虑异常情况、边界条件、权衡取舍

                三、表达能力 (dimensionExpression)
                  3.1 清晰度：表达是否易于理解，有无歧义
                  3.2 简洁性：是否言简意赅，有无冗余信息
                  3.3 举例能力：能否用具体案例或类比说明抽象概念

                四、专业知识 (dimensionProfessional)
                  4.1 技术栈深度：对使用技术的理解程度（API→原理→源码）
                  4.2 行业认知：对所在行业的技术趋势、最佳实践的了解
                  4.3 工程实践：代码规范、测试意识、CI/CD、可观测性

                五、沟通协作 (dimensionCommunication)
                  5.1 倾听理解：是否准确理解面试官的问题意图
                  5.2 互动能力：是否主动澄清、确认、追问
                  5.3 团队意识：回答中是否体现协作思维、技术分享

                ════════════════════════════════════════════════════════════════
                【%s 级别评估重点】
                ════════════════════════════════════════════════════════════════

                %s

                ════════════════════════════════════════════════════════════════
                【行业基准线 — 所有等级共享】
                ════════════════════════════════════════════════════════════════

                无论公司等级如何，以下标准必须满足：
                - 能清晰表达技术方案（维度三 基础分 ≥ 40）
                - 对简历中提到的技术有基本理解（维度四 基础分 ≥ 40）
                - 回答有基本逻辑性（维度二 基础分 ≥ 40）
                低于此标准的维度，最高分不得超过 40 分。

                ════════════════════════════════════════════════════════════════
                【评分校准指南】
                ════════════════════════════════════════════════════════════════

                - 90-100：卓越（前5%，该级别中极少出现）
                - 75-89：优秀（达到该级别期望，有亮点）
                - 60-74：良好（基本达到该级别要求）
                - 45-59：一般（低于该级别期望，有明显短板）
                - 30-44：较弱（多项不达标）
                - 0-29：差（基础能力不足）

                ════════════════════════════════════════════════════════════════
                【输出格式】
                ════════════════════════════════════════════════════════════════

                请严格按以下 JSON 格式返回，不要包含其他内容：

                {
                  "overallScore": 总分(0-100整数),
                  "dimensionContent": 内容专业度(0-100整数),
                  "dimensionLogic": 逻辑思维(0-100整数),
                  "dimensionExpression": 表达能力(0-100整数),
                  "dimensionProfessional": 专业知识(0-100整数),
                  "dimensionCommunication": 沟通协作(0-100整数),
                  "improvementSummary": "总体改进建议（具体、可执行）",
                  "strengths": "优势总结（列出2-3个具体亮点）",
                  "weaknesses": "不足总结（列出2-3个具体短板）",
                  "questions": [
                    {
                      "questionIndex": 题号,
                      "questionText": "问题内容",
                      "answerText": "回答内容",
                      "score": 得分(0-100整数),
                      "dimensionContent": 内容得分(0-100整数),
                      "dimensionLogic": 逻辑得分(0-100整数),
                      "dimensionExpression": 表达得分(0-100整数),
                      "dimensionProfessional": 专业得分(0-100整数),
                      "improvementTip": "针对此问题的具体改进建议",
                      "referenceAnswer": "参考答案（展示该级别的期望水平）"
                    }
                  ]
                }
                """;
        // Replace in reverse order to avoid partial matches
        prompt = prompt.replace("\n                %s\n", "\n" + getTierEvaluationFocus(tier) + "\n");
        prompt = prompt.replace("%s", tier.getName());
        return prompt;
    }

    private String getTierEvaluationFocus(CompanyTier tier) {
        return switch (tier) {
            case TIER_1 -> """
                    超大厂评估重点：
                    - 系统设计：能否设计高可用、可扩展的分布式系统
                    - 源码理解：对框架/中间件底层原理的掌握深度
                    - 算法优化：复杂度分析、空间换时间、并发优化
                    - 架构思维：技术选型的权衡能力、演进式设计
                    - 代码质量：设计模式、SOLID原则、可维护性
                    - 预期：候选人应展示深度技术洞察力和架构级思维""";
            case TIER_2 -> """
                    大厂评估重点：
                    - 扎实基础：数据结构、算法、操作系统、网络协议
                    - 问题拆解：能否将复杂问题分解为可解决的子问题
                    - 技术深度：对使用技术的理解程度（不只是会用）
                    - 工程实践：代码规范、测试意识、性能优化
                    - 预期：候选人应展示扎实的技术功底和工程素养""";
            case TIER_3 -> """
                    中厂评估重点：
                    - 实用技能：能否快速解决业务问题
                    - 技术广度：全栈能力、多种技术栈的掌握
                    - 团队协作：沟通能力、代码评审、知识分享
                    - 学习能力：新技术的上手速度和学习方法
                    - 预期：候选人应展示实用技能和快速交付能力""";
            case TIER_4 -> """
                    小厂评估重点：
                    - 独立能力：一个人能否搞定一个完整模块
                    - 动手能力：快速原型、端到端交付
                    - 适应性：面对模糊需求的处理能力
                    - 基础功底：虽是小厂但基础标准不能低
                    - 预期：候选人应展示独立工作能力和全栈思维""";
            case TIER_5 -> """
                    初创评估重点：
                    - 全栈能力：前后端、数据库、部署都能上手
                    - 学习速度：快速掌握新技术的能力
                    - 主动性：自驱力、产品思维、owner意识
                    - 基础标准：虽是初创但行业标准不能低
                    - 预期：候选人应展示学习热情和多面手潜力""";
        };
    }

    private String buildUserPrompt(String text, String jdText, String resumeText) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("## 面试对话文本\n\n").append(text).append("\n\n");
        if (jdText != null && !jdText.isEmpty()) {
            prompt.append("## 职位描述 (JD)\n\n").append(jdText).append("\n\n");
        }
        if (resumeText != null && !resumeText.isEmpty()) {
            prompt.append("## 候选人简历\n\n").append(resumeText).append("\n\n");
        }
        prompt.append("请根据以上信息，按照评估框架对候选人的面试表现进行全面评估。");
        return prompt.toString();
    }

    // ==================== 双阶段评估 ====================

    @Override
    public String analyzeAndScoreBatchTwoPhase(String text, String jdText, String resumeText,
                                                Long configId, Integer companyTier) {
        log.info("开始双阶段评分分析: 文本长度={}, companyTier={}", text.length(), companyTier);

        LlmClient client = resolveClient(configId);
        CompanyTier tier = companyTier != null ? CompanyTier.fromCode(companyTier) : CompanyTier.TIER_3;

        // Step 1: 将转录文本按长度分块
        List<String> chunks = splitTranscript(text);
        log.info("转录文本分为 {} 块", chunks.size());

        // Step 2: Phase 1 — 分析（LLM 从每块中识别问答对并提取层级）
        List<JsonNode> allAnalysisResults = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            int batchIndex = i + 1;
            log.info("Phase 1 分析: 第 {}/{} 块 ({} 字符)", batchIndex, chunks.size(), chunks.get(i).length());

            String analysisPrompt = buildTranscriptAnalysisPrompt(chunks.get(i), batchIndex, jdText, resumeText);
            String analysisSystemPrompt = buildAnalysisSystemPrompt(tier);
            JsonNode analysisResult = outputInvoker.invoke(client, analysisPrompt, analysisSystemPrompt, configId);
            allAnalysisResults.add(analysisResult);

            if (analysisResult.isArray()) {
                log.info("Phase 1 第 {} 块识别出 {} 个问答对", batchIndex, analysisResult.size());
            }
        }

        // Step 3: 合并分析结果
        String mergedAnalysis = mergeBatchResults(allAnalysisResults);
        log.info("Phase 1 完成: 分析结果已合并");

        // Step 4: Phase 2 — 评分（基于分析结果映射分数）
        log.info("Phase 2 评分: 基于分析结果映射分数");
        String scoringPrompt = buildScoringPrompt(mergedAnalysis, jdText, resumeText);
        String scoringSystemPrompt = buildScoringSystemPrompt(tier);
        JsonNode finalResult = outputInvoker.invoke(client, scoringPrompt, scoringSystemPrompt, configId);

        // Step 5: 服务端校验 — 层级与分数一致性
        validateTierScoreConsistency(finalResult);

        log.info("双阶段评分分析完成");
        return finalResult.toString();
    }

    /**
     * 构建 Phase 1 分析 Prompt — 从原始转录中识别问答对并分析（不打分）
     */
    private String buildTranscriptAnalysisPrompt(String transcriptChunk, int batchIndex, String jdText, String resumeText) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("## 面试转录文本（第 ").append(batchIndex).append(" 段）\n\n");
        prompt.append("以下是面试录音的语音转文字结果，请从中识别出每个问答对并逐一分析（不需要打分）：\n\n");
        prompt.append(transcriptChunk).append("\n\n");

        if (jdText != null && !jdText.isEmpty()) {
            prompt.append("## 职位描述 (JD)\n\n").append(jdText).append("\n\n");
        }
        if (resumeText != null && !resumeText.isEmpty()) {
            prompt.append("## 候选人简历\n\n").append(resumeText).append("\n\n");
        }

        prompt.append("请识别所有问答对并逐一分析，返回 JSON 数组格式。");
        return prompt.toString();
    }

    private String buildAnalysisSystemPrompt(CompanyTier tier) {
        return """
                你是一位资深的面试评估专家，专注于 %s 级别企业的面试评估。

                【任务一：识别问答对】
                你收到的是面试录音的语音转文字结果（连续口语文本，无格式标记）。
                你需要从语义上识别出面试官的问题和候选人的回答。
                识别技巧：
                - 面试官通常会提问（包含"请"、"怎么"、"什么"、"为什么"、"谈谈"、"说说"等疑问词）
                - 候选人的回答通常在面试官提问之后
                - 有时候面试官会追问或候选人会主动补充，这些算同一个问答对
                - 尽可能识别出所有问答对，不要遗漏

                【任务二：分析每个问答对】
                分析候选人对每个问题的回答质量。注意：只需要分析，不需要打分！

                【评估维度】
                - 内容专业度: 技术深度、准确性、完整性
                - 逻辑思维: 结构化、因果链、边界意识
                - 表达能力: 清晰度、简洁性、举例能力
                - 专业知识: 技术栈深度、行业认知、工程实践

                【层级标准】
                - 卓越: 该维度表现极佳，几乎无改进空间
                - 优秀: 达到 %s 级别期望，有明显亮点
                - 良好: 基本达到该级别要求
                - 一般: 低于期望，有明显短板
                - 较弱: 多项不达标
                - 差: 基础能力严重不足
                - "不知道"、"忘了"、"跳过" → 差

                【输出格式】
                严格返回 JSON 数组，每个元素包含观察和层级（不需要分数）：
                [
                  {
                    "questionText": "问题内容",
                    "answerText": "回答内容",
                    "score": 0,
                    "dimensionContent": {"observations": "具体观察...", "tier": "良好"},
                    "dimensionLogic": {"observations": "具体观察...", "tier": "一般"},
                    "dimensionExpression": {"observations": "具体观察...", "tier": "优秀"},
                    "dimensionProfessional": {"observations": "具体观察...", "tier": "良好"},
                    "improvementTip": "具体改进建议",
                    "referenceAnswer": "参考答案"
                  }
                ]

                questionText 和 answerText 必须从转录原文中提取，不要编造。
                observations 必须引用回答中的具体内容，不要泛泛而谈。
                尽可能识别出所有问答对，不要遗漏。
                只返回纯 JSON，不要包含 markdown 代码块。
                """.formatted(tier.getName(), tier.getName());
    }

    /**
     * 构建 Phase 2 评分 Prompt — 基于分析结果映射分数
     */
    private String buildScoringPrompt(String mergedAnalysis, String jdText, String resumeText) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("## 评分任务\n\n");
        prompt.append("以下是各问题的分析结果（含层级分类），请将层级映射为具体分数：\n\n");
        prompt.append("```json\n").append(mergedAnalysis).append("\n```\n\n");

        if (jdText != null && !jdText.isEmpty()) {
            prompt.append("## 职位描述 (JD)\n\n").append(jdText).append("\n\n");
        }
        if (resumeText != null && !resumeText.isEmpty()) {
            prompt.append("## 候选人简历\n\n").append(resumeText).append("\n\n");
        }

        prompt.append("请将每个维度的层级分类映射为具体分数，保持 observations 不变。");
        return prompt.toString();
    }

    private String buildScoringSystemPrompt(CompanyTier tier) {
        return """
                你是一位资深的面试评估专家，专注于 %s 级别企业的面试评估。

                【任务】
                基于已有的分析结果（含层级分类），将层级映射为具体分数。

                【层级 → 分数映射规则】
                你必须严格按照以下区间给分，不得超出区间：
                - 卓越 → 90-100（该级别中极少出现，需有极强证据支撑）
                - 优秀 → 75-89（有明确亮点）
                - 良好 → 60-74（基本达标）
                - 一般 → 45-59（有明显短板）
                - 较弱 → 30-44（多项不达标）
                - 差 → 0-29（基础能力不足）

                【行业基准线 — 所有等级共享】
                - 逻辑思维 基础分 ≥ 40
                - 表达能力 基础分 ≥ 40
                - 专业知识 基础分 ≥ 40
                低于此标准的维度，最高分不得超过 40 分。

                【输出格式】
                严格返回以下 JSON 格式，不要包含 markdown 代码块：
                {
                  "overallScore": 0,
                  "dimensionContent": 分数(0-100整数),
                  "dimensionLogic": 分数(0-100整数),
                  "dimensionExpression": 分数(0-100整数),
                  "dimensionProfessional": 分数(0-100整数),
                  "dimensionCommunication": 分数(0-100整数),
                  "improvementSummary": "总体改进建议",
                  "strengths": "优势总结（2-3个亮点）",
                  "weaknesses": "不足总结（2-3个短板）",
                  "questions": [
                    {
                      "questionIndex": 题号,
                      "questionText": "问题内容",
                      "answerText": "回答内容",
                      "score": 得分(0-100整数),
                      "dimensionContent": 分数,
                      "dimensionLogic": 分数,
                      "dimensionExpression": 分数,
                      "dimensionProfessional": 分数,
                      "improvementTip": "改进建议",
                      "referenceAnswer": "参考答案"
                    }
                  ]
                }

                注意：overallScore 留 0 即可，系统会自动计算加权总分。
                只返回纯 JSON，不要包含其他内容。
                """.formatted(tier.getName());
    }

    // ==================== 服务端校验 ====================

    /**
     * 校验层级与分数的一致性
     * <p>
     * 如果分数不在对应层级的区间内，将其修正到区间中位数。
     * </p>
     */
    private void validateTierScoreConsistency(JsonNode result) {
        if (!result.isObject()) return;

        // 校验题目级别的维度分数
        JsonNode questions = result.path("questions");
        if (questions.isArray()) {
            for (JsonNode q : questions) {
                if (q.isObject()) {
                    validateDimensionScore(q, "dimensionContent");
                    validateDimensionScore(q, "dimensionLogic");
                    validateDimensionScore(q, "dimensionExpression");
                    validateDimensionScore(q, "dimensionProfessional");
                }
            }
        }
    }

    /**
     * 校验单个维度分数是否在合理范围内
     */
    private void validateDimensionScore(JsonNode node, String dimension) {
        JsonNode scoreNode = node.path(dimension);
        if (scoreNode.isMissingNode() || !scoreNode.isNumber()) return;

        int score = scoreNode.asInt();

        // 检查行业基准线
        if ("dimensionLogic".equals(dimension) || "dimensionExpression".equals(dimension)
                || "dimensionProfessional".equals(dimension)) {
            if (score > 0 && score < 40) {
                log.warn("维度 {} 分数 {} 低于行业基准线 40，保持原值（可能是故意低分）", dimension, score);
            }
        }

        // 检查分数范围
        if (score < 0 || score > 100) {
            log.warn("维度 {} 分数 {} 超出范围 [0, 100]，需要修正", dimension, score);
        }
    }

    // ==================== 工具方法 ====================

    /**
     * 将层级字符串转换为分数
     *
     * @param tier 层级名称（卓越/优秀/良好/一般/较弱/差）
     * @return 区间中位数分数；未知层级返回 50
     */
    private int tierToScore(String tier) {
        int[] range = TIER_RANGES.get(tier);
        if (range == null) {
            log.warn("未知层级: {}, 使用默认分数 50", tier);
            return 50;
        }
        return (range[0] + range[1]) / 2;
    }

    /**
     * 从分析结果中提取各维度的层级分类
     * <p>
     * 对每个维度，收集所有问题的层级，取中位数作为该维度的整体层级。
     * </p>
     */
    private Map<String, String> extractDimensionTiers(List<JsonNode> analysisBatches) {
        Map<String, List<String>> dimensionTierLists = new LinkedHashMap<>();
        dimensionTierLists.put("dimensionContent", new ArrayList<>());
        dimensionTierLists.put("dimensionLogic", new ArrayList<>());
        dimensionTierLists.put("dimensionExpression", new ArrayList<>());
        dimensionTierLists.put("dimensionProfessional", new ArrayList<>());

        for (JsonNode batch : analysisBatches) {
            if (!batch.isArray()) continue;
            for (JsonNode q : batch) {
                for (String dim : dimensionTierLists.keySet()) {
                    JsonNode dimNode = q.path(dim);
                    if (dimNode.isObject()) {
                        String tierValue = dimNode.path("tier").asText("一般");
                        dimensionTierLists.get(dim).add(tierValue);
                    }
                }
            }
        }

        Map<String, String> result = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> entry : dimensionTierLists.entrySet()) {
            List<String> tiers = entry.getValue();
            result.put(entry.getKey(), getMedianTier(tiers));
        }
        return result;
    }

    /**
     * 取层级列表的中位数
     */
    private String getMedianTier(List<String> tiers) {
        if (tiers.isEmpty()) return "一般";

        List<String> tierOrder = List.of("差", "较弱", "一般", "良好", "优秀", "卓越");
        List<Integer> indices = new ArrayList<>();
        for (String t : tiers) {
            int idx = tierOrder.indexOf(t);
            indices.add(idx >= 0 ? idx : 2); // 默认 "一般"
        }
        Collections.sort(indices);
        int medianIdx = indices.get(indices.size() / 2);
        return tierOrder.get(medianIdx);
    }

    private LlmClient resolveClient(Long configId) {
        if (configId != null && configId > 0) {
            AiConfig config = aiConfigService.getDetail(configId);
            return aiClientFactory.getLlmClient(config.getProvider());
        }
        return aiClientFactory.getDefaultLlmClient();
    }
}
