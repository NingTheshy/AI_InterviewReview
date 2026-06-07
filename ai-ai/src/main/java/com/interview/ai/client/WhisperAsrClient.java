package com.interview.ai.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.interview.ai.config.AiProviderProperties;
import com.interview.ai.service.AsrClient;
import com.interview.common.constant.ErrorCode;
import com.interview.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.multipart.MultipartFile;

/**
 * Whisper ASR 客户端实现
 * <p>
 * 通过 OpenAI Whisper API 实现语音转文字，支持所有兼容 OpenAI 格式的服务。
 * </p>
 */
@Slf4j
public class WhisperAsrClient implements AsrClient {

    private final WebClient webClient;
    private final AiProviderProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public WhisperAsrClient(AiProviderProperties properties) {
        this.properties = properties;
        this.webClient = WebClient.builder()
                .baseUrl(properties.getApiEndpoint())
                .defaultHeader("Authorization", "Bearer " + properties.getApiKey())
                .build();
    }

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
