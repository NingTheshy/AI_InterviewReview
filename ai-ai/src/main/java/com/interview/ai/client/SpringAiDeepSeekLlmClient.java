package com.interview.ai.client;

import com.interview.ai.config.AiProviderProperties;
import com.interview.ai.service.LlmClient;
import com.interview.common.constant.ErrorCode;
import com.interview.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.ai.deepseek.DeepSeekChatOptions;
import org.springframework.ai.deepseek.api.DeepSeekApi;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * 基于 Spring AI 的 DeepSeek 客户端。
 * <p>
 * 保持 {@link LlmClient} 契约不变，避免影响评分、公司分级和运行时提供商路由。
 * 业务层仍接收纯文本响应；DeepSeek 请求协议、消息模型和响应提取由 Spring AI 负责。
 * </p>
 */
@Slf4j
public class SpringAiDeepSeekLlmClient implements LlmClient {

    private static final int MAX_RETRIES = 3;
    private static final long RETRY_BASE_DELAY_MS = 5000;

    private final AiProviderProperties properties;
    private final DeepSeekChatModel chatModel;

    /** 保持原有调用超时和重试策略，避免改变面试处理的失败语义。 */
    private Duration callTimeout = Duration.ofMinutes(5);

    public SpringAiDeepSeekLlmClient(AiProviderProperties properties) {
        this.properties = properties;

        DeepSeekApi deepSeekApi = DeepSeekApi.builder()
                .baseUrl(properties.getApiEndpoint())
                .apiKey(properties.getApiKey())
                .build();

        DeepSeekChatOptions options = DeepSeekChatOptions.builder()
                .model(properties.getModelName())
                .temperature(0.3)
                .build();

        this.chatModel = DeepSeekChatModel.builder()
                .deepSeekApi(deepSeekApi)
                .defaultOptions(options)
                .build();
    }

    /** 覆盖默认五分钟超时，供不同模型配置复用同一客户端实现。 */
    public void setCallTimeout(Duration timeout) {
        this.callTimeout = timeout;
    }

    @Override
    public String call(String prompt, String systemPrompt, Long configId) {
        long startTime = System.currentTimeMillis();
        Exception lastException = null;

        for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
            try {
                if (attempt > 0) {
                    long delay = RETRY_BASE_DELAY_MS * (1L << (attempt - 1));
                    log.warn("Spring AI DeepSeek 调用重试: attempt={}, delay={}ms, promptLength={}",
                            attempt, delay, prompt.length());
                    Thread.sleep(delay);
                }

                String result = doCall(prompt, systemPrompt);
                if (attempt > 0) {
                    log.info("Spring AI DeepSeek 调用重试成功: attempt={}, totalElapsed={}ms",
                            attempt, System.currentTimeMillis() - startTime);
                }
                return result;
            } catch (BusinessException e) {
                throw e;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new BusinessException(ErrorCode.AI_MODEL_CALL_FAILED.getCode(), "AI 模型调用被中断");
            } catch (Exception e) {
                lastException = e;
                log.warn("Spring AI DeepSeek 调用失败: attempt={}, elapsed={}ms, error={}",
                        attempt, System.currentTimeMillis() - startTime, e.getMessage());
            }
        }

        String message = lastException != null ? lastException.getMessage() : "未知异常";
        log.error("Spring AI DeepSeek 调用最终失败: 已重试{}次, totalElapsed={}ms, error={}",
                MAX_RETRIES, System.currentTimeMillis() - startTime, message);
        throw new BusinessException(ErrorCode.AI_MODEL_CALL_FAILED.getCode(),
                "AI 模型调用异常（已重试" + MAX_RETRIES + "次）: " + message);
    }

    /**
     * 在线程池中执行同步 Spring AI 调用，避免在异步面试线程上阻塞 Reactor 事件循环。
     *
     * <p>系统提示可为空；返回空字符串而非 {@code null}，使上层结构化输出处理器能够统一处理。</p>
     */
    private String doCall(String prompt, String systemPrompt) {
        List<Message> messages = new ArrayList<>();
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            messages.add(new SystemMessage(systemPrompt));
        }
        messages.add(new UserMessage(prompt));

        log.info("Spring AI DeepSeek 调用开始: promptLength={}, model={}, timeout={}s",
                prompt.length(), properties.getModelName(), callTimeout.getSeconds());
        long startTime = System.currentTimeMillis();

        String response = Mono.fromCallable(() -> chatModel.call(new Prompt(messages))
                        .getResult().getOutput().getText())
                .subscribeOn(Schedulers.boundedElastic())
                .timeout(callTimeout)
                .block();

        log.info("Spring AI DeepSeek 调用完成: elapsed={}ms", System.currentTimeMillis() - startTime);
        return response != null ? response : "";
    }

    /** 返回只读配置引用，供诊断和客户端工厂测试确认当前 provider。 */
    public AiProviderProperties getProperties() {
        return properties;
    }
}
