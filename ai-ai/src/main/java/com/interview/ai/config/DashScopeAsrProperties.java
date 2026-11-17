package com.interview.ai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** DashScope 录音文件识别协议配置；具体模型由 AI 配置记录决定。 */
@Data
@Component
@ConfigurationProperties(prefix = "ai.dashscope-asr")
public class DashScopeAsrProperties {
    private String fileSourceMode = "TEMPORARY_UPLOAD";
    private String publicBaseUrl;
    private String fileUrlSecret;
    private int fileUrlTtlSeconds = 10800;
    private boolean diarizationEnabled = true;
    private Integer speakerCount = 2;
    private int maxDiarizationDurationMinutes = 120;
    private int pollIntervalSeconds = 5;
    private int taskTimeoutSeconds = 10800;
    private String ffmpegPath = "ffmpeg";
    /** Paraformer-v2 可选的预编译热词表 ID。 */
    private String paraformerVocabularyId;
}
