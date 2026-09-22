package com.interview.ai.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.interview.ai.config.AiProviderProperties;
import com.interview.ai.config.FunAsrProperties;
import com.interview.ai.dto.TranscriptionResult;
import com.interview.ai.service.WholeFileAsrClient;
import com.interview.common.constant.ErrorCode;
import com.interview.common.exception.BusinessException;
import com.interview.common.utils.AsrFileUrlSigner;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * DashScope Fun-ASR 非实时客户端。
 *
 * <p>Fun-ASR 通过可访问音频 URL 异步转写。开启说话人分离后，必须用完整单声道音频提交，
 * 因此不能复用 MiMo-ASR 的分片上传路径。</p>
 */
@Slf4j
public class FunAsrClient implements WholeFileAsrClient {

    private final AiProviderProperties properties;
    private final FunAsrProperties funAsrProperties;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final FunAsrTranscriptionParser transcriptionParser = new FunAsrTranscriptionParser(objectMapper);
    private final WebClient webClient;

    public FunAsrClient(AiProviderProperties properties, FunAsrProperties funAsrProperties) {
        this.properties = properties;
        this.funAsrProperties = funAsrProperties;
        this.webClient = WebClient.builder()
                .baseUrl(properties.getApiEndpoint())
                .defaultHeader("Authorization", "Bearer " + properties.getApiKey())
                .build();
    }

    @Override
    public String transcribe(MultipartFile audioFile, String language) {
        throw new BusinessException(ErrorCode.AI_MODEL_CALL_FAILED.getCode(),
                "Fun-ASR 非实时转写需要完整音频文件路径，不能使用分片直传");
    }

    @Override
    public String transcribe(Path audioFile, String language) {
        return transcribeDetailed(audioFile, language).text();
    }

    @Override
    public TranscriptionResult transcribeDetailed(Path audioFile, String language) {
        validateUrlConfiguration();
        try {
            String taskId = submitTask(buildSignedAudioUrl(audioFile), language);
            JsonNode taskResult = pollTask(taskId);
            return extractTranscript(taskResult);
        } catch (BusinessException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.AI_MODEL_CALL_FAILED.getCode(), "Fun-ASR 转写被中断");
        } catch (Exception e) {
            log.error("Fun-ASR 转写失败: file={}", audioFile, e);
            throw new BusinessException(ErrorCode.AI_MODEL_CALL_FAILED.getCode(),
                    "Fun-ASR 语音转文字异常: " + e.getMessage());
        }
    }

    /**
     * 返回当前模型在本项目配置下建议接受的最大音频时长。
     *
     * <p>开启说话人分离时遵循 Fun-ASR 的两小时建议；关闭分离时保留更宽松上限，实际限制仍由
     * 上游服务决定。</p>
     */
    @Override
    public Duration maxRecommendedDuration() {
        return funAsrProperties.isDiarizationEnabled()
                ? Duration.ofMinutes(funAsrProperties.getMaxDiarizationDurationMinutes())
                : Duration.ofHours(12);
    }

    /** 提交异步转写任务；音频地址必须为 DashScope 可从公网访问的短时签名 URL。 */
    private String submitTask(String audioUrl, String language) throws Exception {
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("language_hints", List.of(StringUtils.hasText(language) ? language : "zh"));
        parameters.put("diarization_enabled", funAsrProperties.isDiarizationEnabled());
        if (funAsrProperties.isDiarizationEnabled() && funAsrProperties.getSpeakerCount() != null) {
            parameters.put("speaker_count", funAsrProperties.getSpeakerCount());
        }

        Map<String, Object> body = Map.of(
                "model", properties.getModelName(),
                // Fun-ASR 非实时 REST API 要求数组字段 file_urls，即使单次仅提交一个文件。
                "input", Map.of("file_urls", List.of(audioUrl)),
                "parameters", parameters
        );
        String response = webClient.post()
                .uri("/services/audio/asr/transcription")
                .header("X-DashScope-Async", "enable")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(properties.getTimeoutSeconds()))
                .block();

        JsonNode root = objectMapper.readTree(response);
        throwIfApiError(root, "提交任务");
        String taskId = root.path("output").path("task_id").asText();
        if (!StringUtils.hasText(taskId)) {
            throw new BusinessException(ErrorCode.AI_MODEL_CALL_FAILED.getCode(), "Fun-ASR 未返回 task_id");
        }
        log.info("Fun-ASR 任务已提交: taskId={}, diarization={}", taskId,
                funAsrProperties.isDiarizationEnabled());
        return taskId;
    }

    /**
     * 轮询异步任务至成功、失败或签名 URL 到期。
     *
     * <p>轮询截止时间与音频 URL 的有效期一致，避免任务仍在运行时上游无法下载源文件。</p>
     */
    private JsonNode pollTask(String taskId) throws Exception {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(funAsrProperties.getFileUrlTtlSeconds()));
        while (Instant.now().isBefore(deadline)) {
            Thread.sleep(Duration.ofSeconds(funAsrProperties.getPollIntervalSeconds()).toMillis());
            String response = webClient.get()
                    .uri("/tasks/{taskId}", taskId)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(properties.getTimeoutSeconds()))
                    .block();
            JsonNode root = objectMapper.readTree(response);
            throwIfApiError(root, "查询任务");

            String status = root.path("output").path("task_status").asText();
            if ("SUCCEEDED".equals(status)) {
                JsonNode subtask = root.path("output").path("results").path(0);
                String subtaskStatus = subtask.path("subtask_status").asText();
                if (StringUtils.hasText(subtaskStatus) && !"SUCCEEDED".equals(subtaskStatus)) {
                    throw new BusinessException(ErrorCode.AI_MODEL_CALL_FAILED.getCode(),
                            "Fun-ASR 子任务失败: " + subtask.path("message").asText("未知错误"));
                }
                return root;
            }
            if ("FAILED".equals(status) || "UNKNOWN".equals(status)) {
                String message = root.path("output").path("message").asText("未知错误");
                throw new BusinessException(ErrorCode.AI_MODEL_CALL_FAILED.getCode(),
                        "Fun-ASR 任务失败: " + message);
            }
            log.debug("Fun-ASR 任务处理中: taskId={}, status={}", taskId, status);
        }
        throw new BusinessException(ErrorCode.AI_MODEL_CALL_FAILED.getCode(),
                "Fun-ASR 转写超时，请增大 ai.funasr.file-url-ttl-seconds 后重试");
    }

    /** 下载转写结果文件并交给解析器校验句子时间戳和说话人信息。 */
    private TranscriptionResult extractTranscript(JsonNode taskResult) throws Exception {
        String resultUrl = taskResult.path("output").path("result").path("transcription_url").asText();
        if (!StringUtils.hasText(resultUrl)) {
            resultUrl = taskResult.path("output").path("results").path(0).path("transcription_url").asText();
        }
        if (!StringUtils.hasText(resultUrl)) {
            throw new BusinessException(ErrorCode.AI_MODEL_CALL_FAILED.getCode(), "Fun-ASR 未返回转写结果 URL");
        }

        String resultJson = webClient.get().uri(resultUrl).retrieve().bodyToMono(String.class).block();
        return transcriptionParser.parse(resultJson, funAsrProperties.isDiarizationEnabled());
    }

    /**
     * 生成仅授权本次转写任务读取的音频 URL。
     *
     * <p>不暴露原始上传目录，且 URL 到期后即使被记录也不能再次下载。</p>
     */
    private String buildSignedAudioUrl(Path audioFile) {
        String filename = audioFile.getFileName().toString();
        long expiresAt = Instant.now().plusSeconds(funAsrProperties.getFileUrlTtlSeconds()).getEpochSecond();
        String token = AsrFileUrlSigner.sign(filename, expiresAt, funAsrProperties.getFileUrlSecret());
        String baseUrl = funAsrProperties.getPublicBaseUrl().replaceAll("/+$", "");
        return baseUrl + "/asr-files/" + UriUtils.encodePathSegment(filename, StandardCharsets.UTF_8)
                + "?expires=" + expiresAt + "&token=" + token;
    }

    /** 在发起远程调用前拒绝缺失或仍为示例值的公网地址与签名密钥。 */
    private void validateUrlConfiguration() {
        if (!StringUtils.hasText(funAsrProperties.getPublicBaseUrl())
                || !StringUtils.hasText(funAsrProperties.getFileUrlSecret())
                || funAsrProperties.getFileUrlSecret().startsWith("your_")) {
            throw new BusinessException(ErrorCode.AI_MODEL_CALL_FAILED.getCode(),
                    "请配置 FUNASR_PUBLIC_BASE_URL 与 FUNASR_FILE_URL_SECRET，供 Fun-ASR 安全拉取音频");
        }
    }

    /** 将 DashScope 的错误结构转换为统一业务异常，供调用方按模型失败处理。 */
    private void throwIfApiError(JsonNode root, String operation) {
        JsonNode code = root.get("code");
        if (code != null && !code.isNull() && !code.asText().isBlank()) {
            String message = root.path("message").asText("未知错误");
            throw new BusinessException(ErrorCode.AI_MODEL_CALL_FAILED.getCode(),
                    "Fun-ASR " + operation + "失败: " + message);
        }
    }
}
