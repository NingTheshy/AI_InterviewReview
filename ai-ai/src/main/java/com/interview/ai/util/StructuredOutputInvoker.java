package com.interview.ai.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.interview.ai.config.StructuredOutputProperties;
import com.interview.ai.service.LlmClient;
import com.interview.common.constant.ErrorCode;
import com.interview.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 结构化输出调用器
 * <p>
 * 封装 LLM 调用，提供重试和 JSON 修复功能。
 * 解决 LLM 返回格式错误导致整个处理失败的问题。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StructuredOutputInvoker {

    private final StructuredOutputProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String STRICT_JSON_INSTRUCTION =
            "\n\n【重要】请严格返回纯 JSON，不要包含 markdown 代码块(```json)、额外解释或注释。";

    /**
     * 带重试和 JSON 修复的 LLM 调用
     *
     * @param client       LLM 客户端
     * @param prompt       用户提示
     * @param systemPrompt 系统提示
     * @param configId     配置 ID
     * @return 解析后的 JsonNode
     */
    public JsonNode invoke(LlmClient client, String prompt, String systemPrompt, Long configId) {
        int maxAttempts = properties.getMaxRetryAttempts();
        String lastResponse = null;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            String currentPrompt = prompt;
            if (attempt > 1 && properties.isRetryWithStrictInstruction()) {
                currentPrompt = prompt + STRICT_JSON_INSTRUCTION;
                log.info("LLM 调用重试: attempt={}/{}", attempt, maxAttempts);
            }

            String response = client.call(currentPrompt, systemPrompt, configId);

            // 清理响应
            if (properties.isEnableResponseCleanup()) {
                response = cleanJsonResponse(response);
            }

            // 尝试解析 JSON
            try {
                JsonNode node = objectMapper.readTree(response);
                log.info("LLM 调用成功: attempt={}", attempt);
                return node;
            } catch (Exception e) {
                log.warn("JSON 解析失败: attempt={}, error={}", attempt, e.getMessage());
                lastResponse = response;

                // 尝试本地修复
                if (properties.isEnableJsonRepair()) {
                    String repaired = repairJson(response);
                    if (repaired != null) {
                        try {
                            JsonNode node = objectMapper.readTree(repaired);
                            log.info("JSON 修复成功: attempt={}", attempt);
                            return node;
                        } catch (Exception ex) {
                            log.warn("JSON 修复后仍解析失败: attempt={}", attempt);
                        }
                    }
                }
            }
        }

        log.error("LLM 调用失败，已重试 {} 次", maxAttempts);
        throw new BusinessException(ErrorCode.AI_MODEL_CALL_FAILED.getCode(),
                "AI 返回格式异常，请稍后重试");
    }

    /**
     * 清理 LLM 响应中的 markdown 代码块包裹
     */
    private String cleanJsonResponse(String response) {
        if (response == null) return null;

        String trimmed = response.trim();

        // 去除 markdown 代码块包裹
        if (trimmed.startsWith("```")) {
            trimmed = trimmed.replaceAll("^```(json)?\\s*", "").replaceAll("```\\s*$", "");
        }

        // 去除可能的前后缀文本（找到第一个 { 或 [ 和最后一个 } 或 ]）
        int firstBrace = trimmed.indexOf('{');
        int firstBracket = trimmed.indexOf('[');
        int start = -1;
        if (firstBrace >= 0 && firstBracket >= 0) {
            start = Math.min(firstBrace, firstBracket);
        } else if (firstBrace >= 0) {
            start = firstBrace;
        } else if (firstBracket >= 0) {
            start = firstBracket;
        }

        if (start > 0) {
            trimmed = trimmed.substring(start);
        }

        // 找到最后一个 } 或 ]
        int lastBrace = trimmed.lastIndexOf('}');
        int lastBracket = trimmed.lastIndexOf(']');
        int end = Math.max(lastBrace, lastBracket);

        if (end > 0 && end < trimmed.length() - 1) {
            trimmed = trimmed.substring(0, end + 1);
        }

        return trimmed.trim();
    }

    /**
     * 本地 JSON 修复：修复常见的 JSON 格式问题
     */
    private String repairJson(String json) {
        if (json == null || json.isEmpty()) return null;

        try {
            String repaired = json;

            // 修复未转义的控制字符
            repaired = repaired.replaceAll("[\\x00-\\x1F]", " ");

            // 修复尾部逗号
            repaired = repaired.replaceAll(",\\s*([}\\]])", "$1");

            // 修复缺失的闭合括号
            int openBraces = countOccurrences(repaired, '{');
            int closeBraces = countOccurrences(repaired, '}');
            int openBrackets = countOccurrences(repaired, '[');
            int closeBrackets = countOccurrences(repaired, ']');

            while (closeBrackets < openBrackets) {
                repaired += "]";
                closeBrackets++;
            }
            while (closeBraces < openBraces) {
                repaired += "}";
                closeBraces++;
            }

            return repaired;
        } catch (Exception e) {
            return null;
        }
    }

    private int countOccurrences(String str, char c) {
        int count = 0;
        for (char ch : str.toCharArray()) {
            if (ch == c) count++;
        }
        return count;
    }
}
