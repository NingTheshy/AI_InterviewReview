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

import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * 小米 MiMo ASR 客户端实现
 * <p>
 * 小米 ASR 通过 chat/completions 接口实现语音转文字，使用 JSON + base64 格式。
 * 端点：/v1/chat/completions
 * 认证：Authorization: Bearer
 * 请求：JSON body，音频以 base64 编码放在 messages 中
 * </p>
 */
@Slf4j
public class XiaomiAsrClient implements AsrClient {

    private final WebClient webClient;
    private final AiProviderProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public XiaomiAsrClient(AiProviderProperties properties) {
        this.properties = properties;
        this.webClient = WebClient.builder()
                .baseUrl(properties.getApiEndpoint())
                .defaultHeader("Authorization", "Bearer " + properties.getApiKey())
                .build();
    }

    @Override
    public String transcribe(MultipartFile audioFile, String language) {
        try {
            // 将音频文件转为 base64
            byte[] audioBytes = audioFile.getBytes();
            String base64Audio = Base64.getEncoder().encodeToString(audioBytes);

            // 获取 MIME 类型
            String contentType = audioFile.getContentType();
            if (contentType == null || contentType.isEmpty()) {
                contentType = guessContentType(audioFile.getOriginalFilename());
            }

            // 构建请求体（小米 ASR API 格式）
            Map<String, Object> requestBody = Map.of(
                    "model", properties.getModelName(),
                    "messages", List.of(
                            Map.of(
                                    "role", "user",
                                    "content", List.of(
                                            Map.of(
                                                    "type", "input_audio",
                                                    "input_audio", Map.of(
                                                            "data", "data:" + contentType + ";base64," + base64Audio
                                                    )
                                            )
                                    )
                            )
                    ),
                    "asr_options", Map.of(
                            "language", language != null ? language : "zh"
                    )
            );

            String responseJson = webClient.post()
                    .uri("/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            JsonNode root = objectMapper.readTree(responseJson);

            // 检查错误
            if (root.has("error")) {
                String errorMsg = root.path("error").path("message").asText("未知错误");
                log.error("小米 ASR 调用失败: {}", errorMsg);
                throw new BusinessException(ErrorCode.AI_MODEL_CALL_FAILED.getCode(),
                        "语音转文字失败: " + errorMsg);
            }

            // 提取转录文本
            String text = root.path("choices").path(0).path("message").path("content").asText("");
            if (text.isEmpty()) {
                log.error("小米 ASR 未返回转录文本: {}", responseJson);
                throw new BusinessException(ErrorCode.AI_MODEL_CALL_FAILED.getCode(),
                        "语音识别未返回结果");
            }

            log.info("小米 ASR 转录完成: 长度={}", text.length());
            return text;

        } catch (BusinessException e) {
            throw e;
        } catch (org.springframework.web.reactive.function.client.WebClientResponseException e) {
            log.error("小米 ASR HTTP 错误: status={}, body={}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new BusinessException(ErrorCode.AI_MODEL_CALL_FAILED.getCode(),
                    "语音转文字失败: " + e.getStatusCode() + " - " + e.getResponseBodyAsString());
        } catch (Exception e) {
            log.error("小米 ASR 调用异常", e);
            throw new BusinessException(ErrorCode.AI_MODEL_CALL_FAILED.getCode(),
                    "语音转文字异常: " + e.getMessage());
        }
    }

    private String guessContentType(String filename) {
        if (filename == null) return "audio/wav";
        String lower = filename.toLowerCase();
        if (lower.endsWith(".mp3")) return "audio/mpeg";
        if (lower.endsWith(".m4a")) return "audio/mp4";
        if (lower.endsWith(".ogg")) return "audio/ogg";
        if (lower.endsWith(".flac")) return "audio/flac";
        if (lower.endsWith(".webm")) return "audio/webm";
        return "audio/wav";
    }

    public AiProviderProperties getProperties() {
        return properties;
    }
}
