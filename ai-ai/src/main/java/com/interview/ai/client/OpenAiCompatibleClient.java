package com.interview.ai.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.interview.ai.config.AiProviderProperties;
import com.interview.ai.service.AiModelClient;
import com.interview.common.constant.ErrorCode;
import com.interview.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * OpenAI 兼容客户端实现
 * <p>
 * 支持所有兼容 OpenAI API 格式的 LLM 服务（DeepSeek、小米等）。
 * 使用 WebFlux WebClient 进行 HTTP 调用。
 * </p>
 */
@Slf4j
public class OpenAiCompatibleClient implements AiModelClient {

    private final WebClient webClient;
    private final AiProviderProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public OpenAiCompatibleClient(AiProviderProperties properties) {
        this.properties = properties;
        this.webClient = WebClient.builder()
                .baseUrl(properties.getApiEndpoint())
                .defaultHeader("Authorization", "Bearer " + properties.getApiKey())
                .build();
    }

    @Override
    public String call(String prompt, String systemPrompt, Long configId) {
        try {
            List<Map<String, String>> messages = new java.util.ArrayList<>();
            if (systemPrompt != null && !systemPrompt.isEmpty()) {
                messages.add(Map.of("role", "system", "content", systemPrompt));
            }
            messages.add(Map.of("role", "user", "content", prompt));

            Map<String, Object> body = Map.of(
                    "model", properties.getModelName(),
                    "messages", messages,
                    "temperature", 0.3
            );

            String responseJson = webClient.post()
                    .uri("/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            JsonNode root = objectMapper.readTree(responseJson);

            if (root.has("error")) {
                String errorMsg = root.get("error").get("message").asText();
                log.error("AI 模型调用失败: {}", errorMsg);
                throw new BusinessException(ErrorCode.AI_MODEL_CALL_FAILED.getCode(),
                        "AI 模型调用失败: " + errorMsg);
            }

            return root.path("choices").path(0).path("message").path("content").asText("");

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("AI 模型调用异常", e);
            throw new BusinessException(ErrorCode.AI_MODEL_CALL_FAILED.getCode(),
                    "AI 模型调用异常: " + e.getMessage());
        }
    }

    /**
     * 调用 Whisper API 进行语音转文字
     */
    @Override
    public String transcribe(MultipartFile audioFile, String language) {
        try {
            ByteArrayResource fileResource = new ByteArrayResource(audioFile.getBytes()) {
                @Override
                public String getFilename() {
                    return audioFile.getOriginalFilename();
                }
            };

            MultipartBodyBuilder builder = new MultipartBodyBuilder();
            builder.part("file", fileResource);
            builder.part("model", properties.getModelName());
            builder.part("language", language != null ? language : "zh");

            String responseJson = webClient.post()
                    .uri("/audio/transcriptions")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(BodyInserters.fromMultipartData(builder.build()))
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            JsonNode root = objectMapper.readTree(responseJson);

            if (root.has("error")) {
                String errorMsg = root.get("error").get("message").asText();
                log.error("Whisper 转录失败: {}", errorMsg);
                throw new BusinessException(ErrorCode.AI_MODEL_CALL_FAILED.getCode(),
                        "语音转文字失败: " + errorMsg);
            }

            return root.path("text").asText("");

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Whisper 转录异常", e);
            throw new BusinessException(ErrorCode.AI_MODEL_CALL_FAILED.getCode(),
                    "语音转文字异常: " + e.getMessage());
        }
    }

    public AiProviderProperties getProperties() {
        return properties;
    }
}
