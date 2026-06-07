package com.interview.ai.annotation;

import com.interview.common.constant.ConfigType;
import org.springframework.stereotype.Component;

import java.lang.annotation.*;

/**
 * AI 提供商标记注解
 * <p>
 * 用于标记 AI 客户端实现类，支持自动注册到工厂。
 * 通过 {@link #provider()} 指定提供商名称，{@link #type()} 指定服务类型。
 * </p>
 *
 * @see ConfigType
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Component
public @interface AiProvider {

    /**
     * 提供商名称（如 "funasr", "deepseek", "openai"）
     */
    String provider();

    /**
     * 服务类型（ASR 或 LLM）
     */
    ConfigType type();
}
