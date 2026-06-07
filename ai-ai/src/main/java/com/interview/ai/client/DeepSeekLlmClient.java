package com.interview.ai.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.interview.ai.config.AiProviderProperties;
import com.interview.ai.service.LlmClient;
import com.interview.common.constant.ErrorCode;
import com.interview.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * DeepSeek LLM 客户端实现
 * <p>
 * 支持所有兼容 OpenAI API 格式的 LLM 服务（DeepSeek、小米等）。
 * </p>
 */
@Slf4j
public class DeepSeekLlmClient implements LlmClient {

    private final WebClient webClient;
    private final AiProviderProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public DeepSeekLlmClient(AiProviderProperties properties) {
        this.properties = properties;
        this.webClient = WebClient.builder()
                .baseUrl(properties.getApiEndpoint())
                .defaultHeader("Authorization", "Bearer " + properties.getApiKey())
                .build();
    }

    /** LLM 调用超时时间，默认 5 分钟 */
    private Duration callTimeout = Duration.ofMinutes(5);

    /** 最大重试次数 */
    private static final int MAX_RETRIES = 3;

    /** 重试基础延迟（毫秒） */
    private static final long RETRY_BASE_DELAY_MS = 5000;

    public void setCallTimeout(Duration timeout) {
        this.callTimeout = timeout;
    }

    @Override
    public String call(String prompt, String systemPrompt, Long configId) {
        long startTime = System.currentTimeMillis();
        int promptLength = prompt.length();
        Exception lastException = null;

        for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
            try {
                if (attempt > 0) {
                    long delay = RETRY_BASE_DELAY_MS * (1L << (attempt - 1)); // 5s, 10s, 20s
                    log.warn("LLM 调用重试: attempt={}, delay={}ms, promptLength={}", attempt, delay, promptLength);
                    Thread.sleep(delay);
                }

                String result = doCall(prompt, systemPrompt);
                long elapsed = System.currentTimeMillis() - startTime;
                if (attempt > 0) {
                    log.info("LLM 调用重试成功: attempt={}, totalElapsed={}ms", attempt, elapsed);
                }
                return result;

            } catch (BusinessException e) {
                // 业务异常（如 API 返回错误）不重试
                long elapsed = System.currentTimeMillis() - startTime;
                log.error("LLM 调用业务异常: elapsed={}ms, error={}", elapsed, e.getMessage());
                throw e;
            } catch (Exception e) {
                lastException = e;
                long elapsed = System.currentTimeMillis() - startTime;
                log.warn("LLM 调用失败: attempt={}, elapsed={}ms, error={}", attempt, elapsed, e.getMessage());
            }
        }

        long elapsed = System.currentTimeMillis() - startTime;
        log.error("LLM 调用最终失败: 已重试{}次, totalElapsed={}ms, error={}", MAX_RETRIES, elapsed, lastException.getMessage());
        throw new BusinessException(ErrorCode.AI_MODEL_CALL_FAILED.getCode(),
                "AI 模型调用异常（已重试" + MAX_RETRIES + "次）: " + lastException.getMessage());
    }

    /**
     * 执行单次 LLM 调用
     */
    private String doCall(String prompt, String systemPrompt) throws Exception {
        List<Map<String, String>> messages = new ArrayList<>();
        if (systemPrompt != null && !systemPrompt.isEmpty()) {
            messages.add(Map.of("role", "system", "content", systemPrompt));
        }
        messages.add(Map.of("role", "user", "content", prompt));

        Map<String, Object> body = Map.of(
                "model", properties.getModelName(),
                "messages", messages,
                "temperature", 0.3
        );

        log.info("LLM 调用开始: promptLength={}, model={}, timeout={}s",
                prompt.length(), properties.getModelName(), callTimeout.getSeconds());

        long startTime = System.currentTimeMillis();
        String responseJson = webClient.post()
                .uri("/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(callTimeout)
                .block();

        long elapsed = System.currentTimeMillis() - startTime;
        log.info("LLM 调用完成: elapsed={}ms", elapsed);

        JsonNode root = objectMapper.readTree(responseJson);

        if (root.has("error")) {
            String errorMsg = root.get("error").get("message").asText();
            log.error("AI 模型 API 错误: {}", errorMsg);
            throw new BusinessException(ErrorCode.AI_MODEL_CALL_FAILED.getCode(),
                    "AI 模型调用失败: " + errorMsg);
        }

        return root.path("choices").path(0).path("message").path("content").asText("");
    }

    public AiProviderProperties getProperties() {
        return properties;
    }
}
