package com.interview.ai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Fun-ASR 非实时转写专用配置。 */
@Data
@Component
@ConfigurationProperties(prefix = "ai.funasr")
public class FunAsrProperties {

    /** 应用可被 DashScope 访问的公网根地址，例如 https://api.example.com。 */
    private String publicBaseUrl;

    /** 用于生成短时音频下载 URL 的 HMAC 密钥。 */
    private String fileUrlSecret;

    /** 签名 URL 的有效期，必须覆盖提交、排队和识别耗时。 */
    private int fileUrlTtlSeconds = 10800;

    /** 是否开启说话人分离。 */
    private boolean diarizationEnabled = true;

    /** 说话人数参考值；为空时由模型自动识别。 */
    private Integer speakerCount;

    /** 开启说话人分离时的音频时长保护上限。 */
    private int maxDiarizationDurationMinutes = 120;

    /** 轮询任务的间隔。 */
    private int pollIntervalSeconds = 5;

    /** FFmpeg 可执行文件路径；默认从系统 PATH 查找。 */
    private String ffmpegPath = "ffmpeg";
}
