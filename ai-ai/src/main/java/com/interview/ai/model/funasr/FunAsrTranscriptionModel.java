package com.interview.ai.model.funasr;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.interview.ai.client.FunAsrTranscriptionParser;
import com.interview.ai.config.AiProviderProperties;
import com.interview.ai.config.FunAsrProperties;
import com.interview.ai.dto.TranscriptionResult;
import com.interview.common.constant.ErrorCode;
import com.interview.common.exception.BusinessException;
import com.interview.common.utils.AsrFileUrlSigner;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.audio.transcription.AudioTranscription;
import org.springframework.ai.audio.transcription.AudioTranscriptionPrompt;
import org.springframework.ai.audio.transcription.AudioTranscriptionResponse;
import org.springframework.ai.audio.transcription.AudioTranscriptionResponseMetadata;
import org.springframework.ai.audio.transcription.TranscriptionModel;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriUtils;

import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 基于 Spring AI {@link TranscriptionModel} 契约的 DashScope FunASR 实现。
 *
 * <p>Spring AI 1.1.x 尚未提供 FunASR 官方适配器，因此本模型负责协议层的异步任务提交、
 * 轮询和结果下载；上层统一通过 Spring AI 转写模型调用。句子时间戳和说话人信息保存在
 * 响应元数据中，避免退化为纯文本。</p>
 */
@Slf4j
public class FunAsrTranscriptionModel implements TranscriptionModel {

    public static final String TRANSCRIPTION_RESULT_METADATA_KEY = "funasr.transcription-result";

    private final AiProviderProperties properties;
    private final FunAsrProperties funAsrProperties;
    private final ObjectMapper objectMapper;
    private final FunAsrTranscriptionParser transcriptionParser;
    private final RestClient apiClient;
    private final RestClient resultClient;

    public FunAsrTranscriptionModel(AiProviderProperties properties, FunAsrProperties funAsrProperties) {
        this.properties = properties;
        this.funAsrProperties = funAsrProperties;
        this.objectMapper = new ObjectMapper();
        this.transcriptionParser = new FunAsrTranscriptionParser(objectMapper);

        Duration timeout = Duration.ofSeconds(properties.getTimeoutSeconds());
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(timeout).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(timeout);

        this.apiClient = RestClient.builder()
                .baseUrl(properties.getApiEndpoint())
                .requestFactory(requestFactory)
                .defaultHeader("Authorization", "Bearer " + properties.getApiKey())
                .build();
        this.resultClient = RestClient.builder().requestFactory(requestFactory).build();
    }

    @Override
    public AudioTranscriptionResponse call(AudioTranscriptionPrompt prompt) {
        Resource audio = prompt.getInstructions();
        if (audio == null || !StringUtils.hasText(audio.getFilename())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(), "FunASR 转写需要带文件名的音频资源");
        }

        FunAsrTranscriptionOptions options = prompt.getOptions() instanceof FunAsrTranscriptionOptions configured
                ? configured
                : new FunAsrTranscriptionOptions(properties.getModelName(), "zh");
        String model = StringUtils.hasText(options.getModel()) ? options.getModel() : properties.getModelName();
        String language = StringUtils.hasText(options.getLanguage()) ? options.getLanguage() : "zh";

        try {
            boolean temporaryUpload = "TEMPORARY_UPLOAD".equalsIgnoreCase(funAsrProperties.getFileSourceMode());
            if (!temporaryUpload) validateUrlConfiguration();
            String audioUrl = temporaryUpload
                    ? uploadTemporary(audio, model)
                    : buildSignedAudioUrl(audio.getFilename());
            String taskId = submitTask(audioUrl, model, language, temporaryUpload);
            TranscriptionResult result = extractTranscript(pollTask(taskId));
            AudioTranscriptionResponseMetadata metadata = new AudioTranscriptionResponseMetadata();
            metadata.put(TRANSCRIPTION_RESULT_METADATA_KEY, result);
            metadata.put("provider", "dashscope-funasr");
            metadata.put("taskId", taskId);
            return new AudioTranscriptionResponse(new AudioTranscription(result.text()), metadata);
        } catch (BusinessException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.AI_MODEL_CALL_FAILED.getCode(), "FunASR 转写被中断");
        } catch (Exception e) {
            log.error("Spring AI FunASR 转写失败: file={}", audio.getFilename(), e);
            throw new BusinessException(ErrorCode.AI_MODEL_CALL_FAILED.getCode(),
                    "FunASR 语音转文字异常: " + e.getMessage());
        }
    }

    /** 返回当前说话人分离配置下建议接受的最大音频时长。 */
    public Duration maxRecommendedDuration() {
        return funAsrProperties.isDiarizationEnabled()
                ? Duration.ofMinutes(funAsrProperties.getMaxDiarizationDurationMinutes())
                : Duration.ofHours(12);
    }

    private String submitTask(String audioUrl, String model, String language, boolean temporaryUpload) throws Exception {
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("language_hints", List.of(language));
        parameters.put("diarization_enabled", funAsrProperties.isDiarizationEnabled());
        if (funAsrProperties.isDiarizationEnabled() && funAsrProperties.getSpeakerCount() != null) {
            parameters.put("speaker_count", funAsrProperties.getSpeakerCount());
        }

        Map<String, Object> body = Map.of(
                "model", model,
                "input", Map.of("file_urls", List.of(audioUrl)),
                "parameters", parameters
        );
        RestClient.RequestBodySpec request = apiClient.post()
                .uri("/services/audio/asr/transcription")
                .header("X-DashScope-Async", "enable")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body);
        if (temporaryUpload) {
            request.header("X-DashScope-OssResourceResolve", "enable");
        }
        String response = request.retrieve()
                .body(String.class);

        JsonNode root = readJson(response, "提交任务");
        throwIfApiError(root, "提交任务");
        String taskId = root.path("output").path("task_id").asText();
        if (!StringUtils.hasText(taskId)) {
            throw new BusinessException(ErrorCode.AI_MODEL_CALL_FAILED.getCode(), "FunASR 未返回 task_id");
        }
        log.info("Spring AI FunASR 任务已提交: taskId={}, diarization={}", taskId,
                funAsrProperties.isDiarizationEnabled());
        return taskId;
    }

    private JsonNode pollTask(String taskId) throws Exception {
        Instant deadline = Instant.now().plusSeconds(funAsrProperties.getTaskTimeoutSeconds());
        while (Instant.now().isBefore(deadline)) {
            Thread.sleep(Duration.ofSeconds(funAsrProperties.getPollIntervalSeconds()).toMillis());
            String response = apiClient.get()
                    .uri("/tasks/{taskId}", taskId)
                    .retrieve()
                    .body(String.class);
            JsonNode root = readJson(response, "查询任务");
            throwIfApiError(root, "查询任务");

            String status = root.path("output").path("task_status").asText();
            if ("SUCCEEDED".equals(status)) {
                JsonNode subtask = root.path("output").path("results").path(0);
                String subtaskStatus = subtask.path("subtask_status").asText();
                if (StringUtils.hasText(subtaskStatus) && !"SUCCEEDED".equals(subtaskStatus)) {
                    throw new BusinessException(ErrorCode.AI_MODEL_CALL_FAILED.getCode(),
                            "FunASR 子任务失败: " + subtask.path("message").asText("未知错误"));
                }
                return root;
            }
            if ("FAILED".equals(status) || "UNKNOWN".equals(status)) {
                throw new BusinessException(ErrorCode.AI_MODEL_CALL_FAILED.getCode(),
                        "FunASR 任务失败: " + root.path("output").path("message").asText("未知错误"));
            }
            log.debug("Spring AI FunASR 任务处理中: taskId={}, status={}", taskId, status);
        }
        throw new BusinessException(ErrorCode.AI_MODEL_CALL_FAILED.getCode(),
                "FunASR 转写超时，请增大 ai.funasr.task-timeout-seconds 后重试");
    }

    /** 按 DashScope 官方临时存储协议上传本地音频并返回48小时有效的 oss:// URL。 */
    private String uploadTemporary(Resource audio, String model) throws Exception {
        String policyResponse = apiClient.get()
                .uri(uriBuilder -> uriBuilder.path("/uploads")
                        .queryParam("action", "getPolicy")
                        .queryParam("model", model)
                        .build())
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(String.class);
        JsonNode root = readJson(policyResponse, "获取临时上传凭证");
        throwIfApiError(root, "获取临时上传凭证");
        JsonNode data = root.path("data");
        long maxFileSizeMb = data.path("max_file_size_mb").asLong(0);
        long fileSize = audio.contentLength();
        if (maxFileSizeMb > 0 && fileSize > maxFileSizeMb * 1024L * 1024L) {
            throw new BusinessException(ErrorCode.AUDIO_FILE_TOO_LARGE.getCode(),
                    "DashScope临时上传对当前模型限制为" + maxFileSizeMb + "MB，请改用生产存储模式");
        }

        String safeFilename = UUID.randomUUID() + "-" + audio.getFilename().replaceAll("[^a-zA-Z0-9._-]", "_");
        String key = requiredPolicyValue(data, "upload_dir") + "/" + safeFilename;
        MultipartBodyBuilder form = new MultipartBodyBuilder();
        form.part("OSSAccessKeyId", requiredPolicyValue(data, "oss_access_key_id"));
        form.part("policy", requiredPolicyValue(data, "policy"));
        form.part("Signature", requiredPolicyValue(data, "signature"));
        form.part("key", key);
        form.part("x-oss-object-acl", requiredPolicyValue(data, "x_oss_object_acl"));
        form.part("x-oss-forbid-overwrite", requiredPolicyValue(data, "x_oss_forbid_overwrite"));
        form.part("success_action_status", "200");
        form.part("file", audio);
        resultClient.post()
                .uri(requiredPolicyValue(data, "upload_host"))
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(form.build())
                .retrieve()
                .toBodilessEntity();
        return "oss://" + key;
    }

    private String requiredPolicyValue(JsonNode data, String name) {
        String value = data.path(name).asText();
        if (!StringUtils.hasText(value)) {
            throw new BusinessException(ErrorCode.AI_MODEL_CALL_FAILED.getCode(),
                    "DashScope临时上传凭证缺少字段: " + name);
        }
        return value;
    }

    private TranscriptionResult extractTranscript(JsonNode taskResult) throws Exception {
        String resultUrl = taskResult.path("output").path("result").path("transcription_url").asText();
        if (!StringUtils.hasText(resultUrl)) {
            resultUrl = taskResult.path("output").path("results").path(0).path("transcription_url").asText();
        }
        if (!StringUtils.hasText(resultUrl)) {
            throw new BusinessException(ErrorCode.AI_MODEL_CALL_FAILED.getCode(), "FunASR 未返回转写结果 URL");
        }
        String resultJson = resultClient.get().uri(resultUrl).retrieve().body(String.class);
        return transcriptionParser.parse(resultJson, funAsrProperties.isDiarizationEnabled());
    }

    private JsonNode readJson(String response, String operation) throws Exception {
        if (!StringUtils.hasText(response)) {
            throw new BusinessException(ErrorCode.AI_MODEL_CALL_FAILED.getCode(),
                    "FunASR " + operation + "未返回响应");
        }
        return objectMapper.readTree(response);
    }

    private String buildSignedAudioUrl(String filename) {
        long expiresAt = Instant.now().plusSeconds(funAsrProperties.getFileUrlTtlSeconds()).getEpochSecond();
        String token = AsrFileUrlSigner.sign(filename, expiresAt, funAsrProperties.getFileUrlSecret());
        String baseUrl = funAsrProperties.getPublicBaseUrl().replaceAll("/+$", "");
        return baseUrl + "/asr-files/" + UriUtils.encodePathSegment(filename, StandardCharsets.UTF_8)
                + "?expires=" + expiresAt + "&token=" + token;
    }

    private void validateUrlConfiguration() {
        if (!StringUtils.hasText(funAsrProperties.getPublicBaseUrl())
                || !StringUtils.hasText(funAsrProperties.getFileUrlSecret())
                || funAsrProperties.getFileUrlSecret().startsWith("your_")) {
            throw new BusinessException(ErrorCode.AI_MODEL_CALL_FAILED.getCode(),
                    "请配置 FUNASR_PUBLIC_BASE_URL 与 FUNASR_FILE_URL_SECRET，供 FunASR 安全拉取音频");
        }
    }

    private void throwIfApiError(JsonNode root, String operation) {
        JsonNode code = root.get("code");
        if (code != null && !code.isNull() && StringUtils.hasText(code.asText())) {
            throw new BusinessException(ErrorCode.AI_MODEL_CALL_FAILED.getCode(),
                    "FunASR " + operation + "失败: " + root.path("message").asText("未知错误"));
        }
    }
}
