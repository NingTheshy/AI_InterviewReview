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
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * 基于 Spring AI OpenAI 适配器的文本模型客户端。
 *
 * <p>用于 MiMo 等实现 OpenAI Chat Completions 协议的服务。该类保持
 * {@link LlmClient} 合约、重试节奏、超时及异常语义不变；业务层无需感知供应商变化。</p>
 */
@Slf4j
public class SpringAiOpenAiCompatibleLlmClient implements LlmClient {

    private static final int MAX_RETRIES = 3;
    private static final long RETRY_BASE_DELAY_MS = 5000;

    private final AiProviderProperties properties;
    private final OpenAiChatModel chatModel;
    private Duration callTimeout = Duration.ofMinutes(5);

    public SpringAiOpenAiCompatibleLlmClient(AiProviderProperties properties) {
        this.properties = properties;

        OpenAiApi openAiApi = OpenAiApi.builder()
                .baseUrl(properties.getApiEndpoint())
                .apiKey(properties.getApiKey())
                .build();
        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .model(properties.getModelName())
                .temperature(0.3)
                .build();
        this.chatModel = OpenAiChatModel.builder()
                .openAiApi(openAiApi)
                .defaultOptions(options)
                .build();
    }

    /** 与既有 XiaomiLlmClient 的默认超时保持一致。 */
    public void setCallTimeout(Duration timeout) {
        this.callTimeout = timeout;
    }

    @Override
    public String call(String prompt, String systemPrompt, Long configId) {
        long startTime = System.currentTimeMillis();
        int promptLength = prompt.length();
        Exception lastException = null;

        for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
            try {
                if (attempt > 0) {
                    long delay = RETRY_BASE_DELAY_MS * (1L << (attempt - 1));
                    log.warn("Spring AI OpenAI-compatible LLM 调用重试: attempt={}, delay={}ms, promptLength={}",
                            attempt, delay, promptLength);
                    Thread.sleep(delay);
                }

                String result = doCall(prompt, systemPrompt);
                if (attempt > 0) {
                    log.info("Spring AI OpenAI-compatible LLM 调用重试成功: attempt={}, totalElapsed={}ms",
                            attempt, System.currentTimeMillis() - startTime);
                }
                return result;
            } catch (BusinessException e) {
                log.error("Spring AI OpenAI-compatible LLM 调用业务异常: elapsed={}ms, error={}",
                        System.currentTimeMillis() - startTime, e.getMessage());
                throw e;
            } catch (Exception e) {
                lastException = e;
                log.warn("Spring AI OpenAI-compatible LLM 调用失败: attempt={}, elapsed={}ms, error={}",
                        attempt, System.currentTimeMillis() - startTime, e.getMessage());
            }
        }

        log.error("Spring AI OpenAI-compatible LLM 调用最终失败: 已重试{}次, totalElapsed={}ms, error={}",
                MAX_RETRIES, System.currentTimeMillis() - startTime,
                lastException == null ? "unknown" : lastException.getMessage());
        throw new BusinessException(ErrorCode.AI_MODEL_CALL_FAILED.getCode(),
                "AI 模型调用异常（已重试" + MAX_RETRIES + "次）: "
                        + (lastException == null ? "unknown" : lastException.getMessage()));
    }

    /**
     * 将项目统一的提示词契约转换为 Spring AI 消息模型并在线程池中执行。
     *
     * <p>供应商兼容性由 Spring AI OpenAI 适配器处理；此处只保证超时、空响应和异常语义与
     * {@link LlmClient} 合约一致。</p>
     */
    private String doCall(String prompt, String systemPrompt) {
        List<Message> messages = new ArrayList<>();
        if (systemPrompt != null && !systemPrompt.isEmpty()) {
            messages.add(new SystemMessage(systemPrompt));
        }
        messages.add(new UserMessage(prompt));

        log.info("Spring AI OpenAI-compatible LLM 调用开始: promptLength={}, model={}, timeout={}s",
                prompt.length(), properties.getModelName(), callTimeout.getSeconds());
        long startTime = System.currentTimeMillis();
        String result = Mono.fromCallable(() -> chatModel.call(new Prompt(messages))
                        .getResult().getOutput().getText())
                .subscribeOn(Schedulers.boundedElastic())
                .timeout(callTimeout)
                .block();
        log.info("Spring AI OpenAI-compatible LLM 调用完成: elapsed={}ms",
                System.currentTimeMillis() - startTime);
        return result == null ? "" : result;
    }

    /** 返回只读配置引用，供诊断和客户端工厂测试确认当前 provider。 */
    public AiProviderProperties getProperties() {
        return properties;
    }
}
