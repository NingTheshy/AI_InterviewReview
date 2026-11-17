package com.interview.ai.model.dashscope;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.interview.ai.client.DashScopeTranscriptionParser;
import com.interview.ai.config.AiProviderProperties;
import com.interview.ai.config.DashScopeAsrProperties;
import com.interview.ai.dto.AsrTranscriptionContext;
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
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriUtils;

import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * DashScope 录音文件识别的 Spring AI {@link TranscriptionModel} 实现。
 *
 * <p>FunASR 是模型集合而非本系统的 provider 或固定模型名。当前仅允许已经完成业务评估的
 * Qwen Audio 3.1（主模型）和 Paraformer-v2（备选模型），二者共用 DashScope 异步协议。</p>
 */
@Slf4j
public class DashScopeFileTranscriptionModel implements TranscriptionModel {

    public static final String QWEN_MODEL = "qwen-audio-3.1-asr-flash-filetrans";
    public static final String PARAFORMER_MODEL = "paraformer-v2";
    public static final Set<String> SUPPORTED_MODELS = Set.of(QWEN_MODEL, PARAFORMER_MODEL);
    public static final String TRANSCRIPTION_RESULT_METADATA_KEY = "dashscope.transcription-result";

    private final AiProviderProperties properties;
    private final DashScopeAsrProperties asrProperties;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final DashScopeTranscriptionParser transcriptionParser = new DashScopeTranscriptionParser(objectMapper);
    private final RestClient apiClient;
    private final RestClient resultClient;

    public DashScopeFileTranscriptionModel(AiProviderProperties properties,
                                           DashScopeAsrProperties asrProperties) {
        this.properties = properties;
        this.asrProperties = asrProperties;
        Duration timeout = Duration.ofSeconds(properties.getTimeoutSeconds());
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(timeout).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(timeout);
        this.apiClient = RestClient.builder().baseUrl(properties.getApiEndpoint())
                .requestFactory(requestFactory)
                .defaultHeader("Authorization", "Bearer " + properties.getApiKey()).build();
        this.resultClient = RestClient.builder().requestFactory(requestFactory).build();
    }

    @Override
    public AudioTranscriptionResponse call(AudioTranscriptionPrompt prompt) {
        Resource audio = prompt.getInstructions();
        if (audio == null || !StringUtils.hasText(audio.getFilename())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(), "DashScope ASR 需要带文件名的音频资源");
        }
        DashScopeTranscriptionOptions options = prompt.getOptions() instanceof DashScopeTranscriptionOptions configured
                ? configured : new DashScopeTranscriptionOptions(properties.getModelName(),
                AsrTranscriptionContext.chinese());
        String model = StringUtils.hasText(options.getModel()) ? options.getModel() : properties.getModelName();
        if (!SUPPORTED_MODELS.contains(model)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(), "不支持的 DashScope ASR 模型: " + model);
        }
        AsrTranscriptionContext context = options.context() == null
                ? AsrTranscriptionContext.chinese() : options.context();
        try {
            boolean temporaryUpload = "TEMPORARY_UPLOAD".equalsIgnoreCase(asrProperties.getFileSourceMode());
            if (!temporaryUpload) validateUrlConfiguration();
            String audioUrl = temporaryUpload ? uploadTemporary(audio, model) : buildSignedAudioUrl(audio.getFilename());
            String taskId = submitTask(audioUrl, model, context, temporaryUpload);
            TranscriptionResult result = extractTranscript(pollTask(taskId));
            AudioTranscriptionResponseMetadata metadata = new AudioTranscriptionResponseMetadata();
            metadata.put(TRANSCRIPTION_RESULT_METADATA_KEY, result);
            metadata.put("provider", "dashscope");
            metadata.put("model", model);
            metadata.put("taskId", taskId);
            return new AudioTranscriptionResponse(new AudioTranscription(result.text()), metadata);
        } catch (BusinessException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.AI_MODEL_CALL_FAILED.getCode(), "DashScope ASR 转写被中断");
        } catch (Exception e) {
            log.error("DashScope ASR 转写失败: model={}, file={}", model, audio.getFilename(), e);
            throw new BusinessException(ErrorCode.AI_MODEL_CALL_FAILED.getCode(),
                    "DashScope ASR 异常: " + e.getMessage());
        }
    }

    public Duration maxRecommendedDuration() {
        return asrProperties.isDiarizationEnabled()
                ? Duration.ofMinutes(asrProperties.getMaxDiarizationDurationMinutes()) : Duration.ofHours(12);
    }

    private String submitTask(String audioUrl, String model, AsrTranscriptionContext context,
                              boolean temporaryUpload) throws Exception {
        Map<String, Object> body = buildRequestBody(audioUrl, model, context);
        RestClient.RequestBodySpec request = apiClient.post().uri("/services/audio/asr/transcription")
                .header("X-DashScope-Async", "enable").contentType(MediaType.APPLICATION_JSON).body(body);
        if (temporaryUpload) request.header("X-DashScope-OssResourceResolve", "enable");
        JsonNode root = readJson(request.retrieve().body(String.class), "提交任务");
        throwIfApiError(root, "提交任务");
        String taskId = root.path("output").path("task_id").asText();
        if (!StringUtils.hasText(taskId)) {
            throw new BusinessException(ErrorCode.AI_MODEL_CALL_FAILED.getCode(), "DashScope ASR 未返回 task_id");
        }
        log.info("DashScope ASR 任务已提交: taskId={}, model={}, diarization={}",
                taskId, model, asrProperties.isDiarizationEnabled());
        return taskId;
    }

    /** 构造模型专属请求，保持可测试且不把厂商参数泄漏到业务层。 */
    Map<String, Object> buildRequestBody(String audioUrl, String model, AsrTranscriptionContext context) {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("file_urls", List.of(audioUrl));
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("channel_id", List.of(0));
        parameters.put("diarization_enabled", asrProperties.isDiarizationEnabled());
        if (asrProperties.isDiarizationEnabled() && asrProperties.getSpeakerCount() != null) {
            parameters.put("speaker_count", asrProperties.getSpeakerCount());
        }
        if (QWEN_MODEL.equals(model)) {
            if (!context.languageHints().isEmpty()) {
                parameters.put("language_hints", List.of(context.languageHints().get(0)));
            }
            if (!context.vocabulary().isEmpty()) parameters.put("vocabulary", context.vocabulary());
            if (StringUtils.hasText(context.contextText())) {
                input.put("context", List.of(Map.of("role", "user", "content", List.of(
                        Map.of("type", "input_text", "text", context.contextText())))));
            }
        } else {
            LinkedHashSet<String> paraformerLanguages = new LinkedHashSet<>(context.languageHints());
            paraformerLanguages.add("zh");
            paraformerLanguages.add("en");
            parameters.put("language_hints", List.copyOf(paraformerLanguages));
            parameters.put("timestamp_alignment_enabled", true);
            String vocabularyId = StringUtils.hasText(context.vocabularyId())
                    ? context.vocabularyId() : asrProperties.getParaformerVocabularyId();
            if (StringUtils.hasText(vocabularyId)) parameters.put("vocabulary_id", vocabularyId);
        }
        return Map.of("model", model, "input", input, "parameters", parameters);
    }

    private JsonNode pollTask(String taskId) throws Exception {
        Instant deadline = Instant.now().plusSeconds(asrProperties.getTaskTimeoutSeconds());
        while (Instant.now().isBefore(deadline)) {
            Thread.sleep(Duration.ofSeconds(asrProperties.getPollIntervalSeconds()).toMillis());
            JsonNode root = readJson(apiClient.get().uri("/tasks/{taskId}", taskId).retrieve().body(String.class),
                    "查询任务");
            throwIfApiError(root, "查询任务");
            String status = root.path("output").path("task_status").asText();
            if ("SUCCEEDED".equals(status)) {
                JsonNode subtask = root.path("output").path("results").path(0);
                String subtaskStatus = subtask.path("subtask_status").asText();
                if (StringUtils.hasText(subtaskStatus) && !"SUCCEEDED".equals(subtaskStatus)) {
                    throw new BusinessException(ErrorCode.AI_MODEL_CALL_FAILED.getCode(),
                            "DashScope ASR 子任务失败: " + subtask.path("message").asText("未知错误"));
                }
                return root;
            }
            if ("FAILED".equals(status) || "UNKNOWN".equals(status)) {
                throw new BusinessException(ErrorCode.AI_MODEL_CALL_FAILED.getCode(),
                        "DashScope ASR 任务失败: " + root.path("output").path("message").asText("未知错误"));
            }
        }
        throw new BusinessException(ErrorCode.AI_MODEL_CALL_FAILED.getCode(),
                "DashScope ASR 转写超时，请增大 ai.dashscope-asr.task-timeout-seconds 后重试");
    }

    private String uploadTemporary(Resource audio, String model) throws Exception {
        JsonNode root = readJson(apiClient.get().uri(uriBuilder -> uriBuilder.path("/uploads")
                .queryParam("action", "getPolicy").queryParam("model", model).build())
                .accept(MediaType.APPLICATION_JSON).retrieve().body(String.class), "获取临时上传凭证");
        throwIfApiError(root, "获取临时上传凭证");
        JsonNode data = root.path("data");
        long maxFileSizeMb = data.path("max_file_size_mb").asLong(0);
        if (maxFileSizeMb > 0 && audio.contentLength() > maxFileSizeMb * 1024L * 1024L) {
            throw new BusinessException(ErrorCode.AUDIO_FILE_TOO_LARGE.getCode(),
                    "DashScope 临时上传对当前模型限制为" + maxFileSizeMb + "MB，请改用生产存储模式");
        }
        String safeName = UUID.randomUUID() + "-" + audio.getFilename().replaceAll("[^a-zA-Z0-9._-]", "_");
        String key = requiredPolicyValue(data, "upload_dir") + "/" + safeName;
        MultipartBodyBuilder form = new MultipartBodyBuilder();
        form.part("OSSAccessKeyId", requiredPolicyValue(data, "oss_access_key_id"));
        form.part("policy", requiredPolicyValue(data, "policy"));
        form.part("Signature", requiredPolicyValue(data, "signature"));
        form.part("key", key);
        form.part("x-oss-object-acl", requiredPolicyValue(data, "x_oss_object_acl"));
        form.part("x-oss-forbid-overwrite", requiredPolicyValue(data, "x_oss_forbid_overwrite"));
        form.part("success_action_status", "200");
        form.part("file", audio);
        resultClient.post().uri(requiredPolicyValue(data, "upload_host"))
                .contentType(MediaType.MULTIPART_FORM_DATA).body(form.build()).retrieve().toBodilessEntity();
        return "oss://" + key;
    }

    private String requiredPolicyValue(JsonNode data, String name) {
        String value = data.path(name).asText();
        if (!StringUtils.hasText(value)) throw new BusinessException(ErrorCode.AI_MODEL_CALL_FAILED.getCode(),
                "DashScope 临时上传凭证缺少字段: " + name);
        return value;
    }

    private TranscriptionResult extractTranscript(JsonNode taskResult) throws Exception {
        String url = taskResult.path("output").path("result").path("transcription_url").asText();
        if (!StringUtils.hasText(url)) {
            url = taskResult.path("output").path("results").path(0).path("transcription_url").asText();
        }
        if (!StringUtils.hasText(url)) throw new BusinessException(ErrorCode.AI_MODEL_CALL_FAILED.getCode(),
                "DashScope ASR 未返回转写结果 URL");
        return transcriptionParser.parse(downloadTranscriptionResult(url),
                asrProperties.isDiarizationEnabled());
    }

    /** OSS 结果链接已经带签名和编码，传入 URI 避免 RestClient 对字符串地址再次编码。 */
    String downloadTranscriptionResult(String signedUrl) {
        try {
            return resultClient.get().uri(URI.create(signedUrl)).retrieve().body(String.class);
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == 403
                    && e.getResponseBodyAsString().contains("<Code>SignatureDoesNotMatch</Code>")) {
                throw new BusinessException(ErrorCode.AI_MODEL_CALL_FAILED.getCode(),
                        "DashScope ASR 任务已完成，但结果下载的 OSS 签名校验失败；请重新测试，若持续失败请检查代理是否改写结果地址");
            }
            throw e;
        }
    }

    private JsonNode readJson(String response, String operation) throws Exception {
        if (!StringUtils.hasText(response)) throw new BusinessException(ErrorCode.AI_MODEL_CALL_FAILED.getCode(),
                "DashScope ASR " + operation + "未返回响应");
        return objectMapper.readTree(response);
    }

    private String buildSignedAudioUrl(String filename) {
        long expiresAt = Instant.now().plusSeconds(asrProperties.getFileUrlTtlSeconds()).getEpochSecond();
        String token = AsrFileUrlSigner.sign(filename, expiresAt, asrProperties.getFileUrlSecret());
        return asrProperties.getPublicBaseUrl().replaceAll("/+$", "") + "/asr-files/"
                + UriUtils.encodePathSegment(filename, StandardCharsets.UTF_8)
                + "?expires=" + expiresAt + "&token=" + token;
    }

    private void validateUrlConfiguration() {
        if (!StringUtils.hasText(asrProperties.getPublicBaseUrl())
                || !StringUtils.hasText(asrProperties.getFileUrlSecret())
                || asrProperties.getFileUrlSecret().startsWith("your_")) {
            throw new BusinessException(ErrorCode.AI_MODEL_CALL_FAILED.getCode(),
                    "请配置 DASHSCOPE_ASR_PUBLIC_BASE_URL 与 DASHSCOPE_ASR_FILE_URL_SECRET");
        }
    }

    private void throwIfApiError(JsonNode root, String operation) {
        JsonNode code = root.get("code");
        if (code != null && !code.isNull() && StringUtils.hasText(code.asText())) {
            throw new BusinessException(ErrorCode.AI_MODEL_CALL_FAILED.getCode(),
                    "DashScope ASR " + operation + "失败: " + root.path("message").asText("未知错误"));
        }
    }
}
