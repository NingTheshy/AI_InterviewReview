package com.interview.ai.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.interview.ai.config.AiProviderProperties;
import com.interview.ai.service.AsrClient;
import com.interview.common.constant.ErrorCode;
import com.interview.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;

import java.util.Base64;
import java.util.Map;

/**
 * DashScope ASR 客户端实现
 * <p>
 * 通过阿里云 DashScope API 实现语音转文字，采用异步提交+轮询模式。
 * 支持网络异常重试（3次指数退避）和可配置的超时时间。
 * </p>
 */
@Slf4j
public class DashScopeAsrClient implements AsrClient {

    /** 最大轮询次数（可通过构造参数配置） */
    private final int maxPollAttempts;
    /** 轮询间隔毫秒（可通过构造参数配置） */
    private final long pollIntervalMs;
    /** 网络异常最大重试次数 */
    private static final int MAX_NETWORK_RETRIES = 3;

    private final WebClient webClient;
    private final AiProviderProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public DashScopeAsrClient(AiProviderProperties properties) {
        this(properties, 150, 2000);
    }

    public DashScopeAsrClient(AiProviderProperties properties, int maxPollAttempts, long pollIntervalMs) {
        this.properties = properties;
        this.maxPollAttempts = maxPollAttempts;
        this.pollIntervalMs = pollIntervalMs;
        this.webClient = WebClient.builder()
                .baseUrl(properties.getApiEndpoint())
                .defaultHeader("Authorization", "Bearer " + properties.getApiKey())
                .build();
    }

    @Override
    public String transcribe(MultipartFile audioFile, String language) {
        try {
            String taskId = submitTranscriptionTaskWithRetry(audioFile, language);
            return pollTaskResultWithRetry(taskId);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("DashScope ASR 调用异常", e);
            throw new BusinessException(ErrorCode.AI_MODEL_CALL_FAILED.getCode(),
                    "语音转文字异常: " + e.getMessage());
        }
    }

    /**
     * 提交转写任务（带网络异常重试）
     */
    private String submitTranscriptionTaskWithRetry(MultipartFile audioFile, String language) throws Exception {
        Exception lastException = null;
        for (int attempt = 1; attempt <= MAX_NETWORK_RETRIES; attempt++) {
            try {
                return submitTranscriptionTask(audioFile, language);
            } catch (WebClientRequestException e) {
                lastException = e;
                log.warn("DashScope 提交任务网络异常: attempt={}/{}, error={}",
                        attempt, MAX_NETWORK_RETRIES, e.getMessage());
                if (attempt < MAX_NETWORK_RETRIES) {
                    Thread.sleep(1000L * attempt); // 指数退避
                }
            }
        }
        throw new BusinessException(ErrorCode.AI_MODEL_CALL_FAILED.getCode(),
                "DashScope 提交任务网络异常，请检查网络后重试");
    }

    private String submitTranscriptionTask(MultipartFile audioFile, String language) throws Exception {
        String base64Data = Base64.getEncoder().encodeToString(audioFile.getBytes());
        String contentType = audioFile.getContentType() != null ? audioFile.getContentType() : "audio/wav";
        String dataUrl = "data:" + contentType + ";base64," + base64Data;

        Map<String, Object> body = Map.of(
                "model", properties.getModelName(),
                "input", Map.of("file_urls", new String[]{dataUrl}),
                "parameters", Map.of("language_hints", new String[]{language != null ? language : "zh"})
        );

        String responseJson = webClient.post()
                .uri("/services/audio/asr/transcription")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(String.class)
                .block();

        JsonNode root = objectMapper.readTree(responseJson);

        if (root.has("code")) {
            String code = root.get("code").asText();
            String message = root.get("message").asText();
            log.error("DashScope 提交任务失败: code={}, message={}", code, message);
            throw new BusinessException(ErrorCode.AI_MODEL_CALL_FAILED.getCode(),
                    "DashScope 提交任务失败: " + message);
        }

        String taskId = root.path("output").path("task_id").asText(null);
        if (taskId == null || taskId.isEmpty()) {
            log.error("DashScope 未返回 task_id: {}", responseJson);
            throw new BusinessException(ErrorCode.AI_MODEL_CALL_FAILED.getCode(),
                    "DashScope 未返回 task_id");
        }

        log.info("DashScope ASR 任务已提交: taskId={}", taskId);
        return taskId;
    }

    /**
     * 轮询任务结果（带网络异常重试）
     */
    private String pollTaskResultWithRetry(String taskId) throws Exception {
        int consecutiveNetworkErrors = 0;

        for (int i = 0; i < maxPollAttempts; i++) {
            Thread.sleep(pollIntervalMs);

            try {
                String responseJson = webClient.get()
                        .uri("/tasks/{taskId}", taskId)
                        .retrieve()
                        .bodyToMono(String.class)
                        .block();

                consecutiveNetworkErrors = 0; // 重置网络错误计数
                JsonNode root = objectMapper.readTree(responseJson);
                String status = root.path("output").path("task_status").asText("UNKNOWN");

                if ("SUCCEEDED".equals(status)) {
                    return extractTranscriptionText(root);
                } else if ("FAILED".equals(status)) {
                    String message = root.path("output").path("message").asText("未知错误");
                    log.error("DashScope ASR 任务失败: taskId={}, message={}", taskId, message);
                    throw new BusinessException(ErrorCode.AI_MODEL_CALL_FAILED.getCode(),
                            "语音识别失败: " + message);
                }

                log.debug("DashScope ASR 任务处理中: taskId={}, status={}, attempt={}/{}",
                        taskId, status, i + 1, maxPollAttempts);

            } catch (WebClientRequestException e) {
                consecutiveNetworkErrors++;
                log.warn("DashScope 轮询网络异常: attempt={}, consecutiveErrors={}",
                        i + 1, consecutiveNetworkErrors);

                if (consecutiveNetworkErrors >= MAX_NETWORK_RETRIES) {
                    log.error("DashScope 轮询连续网络异常 {} 次，放弃", MAX_NETWORK_RETRIES);
                    throw new BusinessException(ErrorCode.AI_MODEL_CALL_FAILED.getCode(),
                            "语音识别网络异常，请稍后重试");
                }
                // 继续轮询，不中断
            }
        }

        throw new BusinessException(ErrorCode.AI_MODEL_CALL_FAILED.getCode(),
                "语音识别超时（" + (maxPollAttempts * pollIntervalMs / 1000) + "秒），请稍后重试");
    }

    private String extractTranscriptionText(JsonNode root) throws Exception {
        JsonNode results = root.path("output").path("results");
        if (results.isArray() && results.size() > 0) {
            String transcriptionUrl = results.get(0).path("transcription_url").asText(null);
            if (transcriptionUrl != null) {
                String transcriptionJson = webClient.get()
                        .uri(transcriptionUrl)
                        .retrieve()
                        .bodyToMono(String.class)
                        .block();

                JsonNode transRoot = objectMapper.readTree(transcriptionJson);
                StringBuilder sb = new StringBuilder();
                JsonNode transcriptions = transRoot.path("transcriptions");
                if (transcriptions.isArray()) {
                    for (JsonNode item : transcriptions) {
                        String text = item.path("text").asText("");
                        if (!text.isEmpty()) {
                            if (!sb.isEmpty()) sb.append("\n");
                            sb.append(text);
                        }
                    }
                }
                if (sb.length() > 0) {
                    return sb.toString();
                }
                return transRoot.path("text").asText("");
            }
        }
        return root.path("output").path("text").asText("");
    }

    public AiProviderProperties getProperties() {
        return properties;
    }
}
