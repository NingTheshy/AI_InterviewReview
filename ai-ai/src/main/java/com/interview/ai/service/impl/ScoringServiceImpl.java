package com.interview.ai.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.interview.ai.entity.AiConfig;
import com.interview.ai.factory.AiClientFactory;
import com.interview.ai.service.AiConfigService;
import com.interview.ai.service.AiModelClient;
import com.interview.ai.service.ScoringService;
import com.interview.ai.util.StructuredOutputInvoker;
import com.interview.common.constant.CompanyTier;
import com.interview.common.constant.ConfigType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
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

    @Override
    public String analyzeAndScore(String text, String jdText, String resumeText, Long configId) {
        return analyzeAndScore(text, jdText, resumeText, configId, null);
    }

    @Override
    public String analyzeAndScore(String text, String jdText, String resumeText, Long configId, Integer companyTier) {
        log.info("开始 AI 评分分析: 文本长度={}, configId={}, companyTier={}", text.length(), configId, companyTier);

        String systemPrompt = buildSystemPrompt(companyTier);
        String userPrompt = buildUserPrompt(text, jdText, resumeText);

        AiModelClient client = resolveClient(configId);
        String result = client.call(userPrompt, systemPrompt, configId);
        log.info("AI 评分分析完成");
        return result;
    }

    @Override
    public String analyzeAndScoreBatch(String text, String jdText, String resumeText, Long configId, Integer companyTier) {
        log.info("开始分批 AI 评分分析: 文本长度={}, companyTier={}", text.length(), companyTier);

        AiModelClient client = resolveClient(configId);

        // Step 1: 拆分面试文本为独立问题段落
        List<String> segments = splitIntoSegments(text);
        log.info("拆分为 {} 个问题段落", segments.size());

        // Step 2: 分批评估（使用 StructuredOutputInvoker 带重试）
        List<JsonNode> batchResults = new ArrayList<>();
        for (int i = 0; i < segments.size(); i += BATCH_SIZE) {
            List<String> batch = segments.subList(i, Math.min(i + BATCH_SIZE, segments.size()));
            int batchIndex = i / BATCH_SIZE + 1;
            int totalBatches = (segments.size() + BATCH_SIZE - 1) / BATCH_SIZE;

            log.info("评估第 {}/{} 批 ({} 个问题)", batchIndex, totalBatches, batch.size());

            String batchPrompt = buildBatchPrompt(batch, batchIndex, jdText, resumeText);
            String batchSystemPrompt = buildBatchSystemPrompt(companyTier);

            JsonNode batchResult = outputInvoker.invoke(client, batchPrompt, batchSystemPrompt, configId);
            batchResults.add(batchResult);
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
     * 将面试文本拆分为独立的问题段落
     */
    private List<String> splitIntoSegments(String text) {
        List<String> segments = new ArrayList<>();

        // 按常见问答分隔模式拆分
        // 模式1: "问：" 或 "Q：" 开头的段落
        // 模式2: 空行分隔的段落
        // 模式3: 数字序号开头 (1. 2. 等)

        String[] lines = text.split("\n");
        StringBuilder currentSegment = new StringBuilder();

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                if (currentSegment.length() > 0) {
                    segments.add(currentSegment.toString().trim());
                    currentSegment = new StringBuilder();
                }
                continue;
            }

            // 检测新的问答对开始
            if (isQuestionStart(trimmed) && currentSegment.length() > 50) {
                segments.add(currentSegment.toString().trim());
                currentSegment = new StringBuilder();
            }

            currentSegment.append(line).append("\n");
        }

        if (currentSegment.length() > 0) {
            segments.add(currentSegment.toString().trim());
        }

        // 如果拆分失败（只有一个段落），按字符长度强制拆分
        if (segments.size() <= 1 && text.length() > 2000) {
            return forceSplit(text, 2000);
        }

        return segments;
    }

    private boolean isQuestionStart(String line) {
        // 匹配常见的问答开始模式
        return line.matches("^(问|Q|面试官|问题|\\d+[.、])[:：].*")
                || line.matches("^(问|Q)\\d+[:：].*")
                || line.matches("^\\d+[.、]\\s*(请|怎么|如何|什么|为什么|谈谈|说说).*");
    }

    private List<String> forceSplit(String text, int chunkSize) {
        List<String> chunks = new ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + chunkSize, text.length());
            chunks.add(text.substring(start, end));
            start = end;
        }
        return chunks;
    }

    private String buildBatchPrompt(List<String> segments, int batchIndex, String jdText, String resumeText) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("## 本批评估任务\n\n");
        prompt.append("以下是第 ").append(batchIndex).append(" 批面试问答，请逐一评估：\n\n");

        for (int i = 0; i < segments.size(); i++) {
            prompt.append("### 问题 ").append(i + 1).append("\n\n");
            prompt.append(segments.get(i)).append("\n\n");
        }

        if (jdText != null && !jdText.isEmpty()) {
            prompt.append("## 职位描述 (JD)\n\n").append(jdText).append("\n\n");
        }
        if (resumeText != null && !resumeText.isEmpty()) {
            prompt.append("## 候选人简历\n\n").append(resumeText).append("\n\n");
        }

        prompt.append("请对每个问题独立评分，返回 JSON 数组格式。");
        return prompt.toString();
    }

    private String buildBatchSystemPrompt(Integer companyTier) {
        CompanyTier tier = companyTier != null ? CompanyTier.fromCode(companyTier) : CompanyTier.TIER_3;

        return """
                你是一位资深的面试评估专家，专注于 %s 级别企业的面试评估。

                【评估维度】
                - 内容专业度 (dimensionContent): 技术深度、准确性、完整性
                - 逻辑思维 (dimensionLogic): 结构化、因果链、边界意识
                - 表达能力 (dimensionExpression): 清晰度、简洁性、举例能力
                - 专业知识 (dimensionProfessional): 技术栈深度、行业认知、工程实践

                【评分校准】
                - 90-100：卓越  |  75-89：优秀  |  60-74：良好
                - 45-59：一般  |  30-44：较弱  |  0-29：差
                - "不知道"、"忘了"、"跳过" 等回答，该题最高 0 分

                【输出格式】
                请严格返回 JSON 数组，每个元素对应一个问题的评估：
                [
                  {
                    "questionText": "问题内容",
                    "answerText": "回答内容",
                    "score": 0-100整数,
                    "dimensionContent": 0-100整数,
                    "dimensionLogic": 0-100整数,
                    "dimensionExpression": 0-100整数,
                    "dimensionProfessional": 0-100整数,
                    "improvementTip": "具体改进建议",
                    "referenceAnswer": "参考答案"
                  }
                ]

                不要包含 markdown 代码块或其他内容，只返回纯 JSON 数组。
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

    private AiModelClient resolveClient(Long configId) {
        if (configId != null && configId > 0) {
            AiConfig config = aiConfigService.getDetail(configId);
            return aiClientFactory.getClient(config.getProvider());
        }
        return aiClientFactory.getDefaultClient(ConfigType.LLM.getCode());
    }
}
