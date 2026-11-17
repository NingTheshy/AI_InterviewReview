package com.interview.ai.dto;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 与供应商无关的 ASR 运行时上下文。
 *
 * <p>业务层只描述语言、岗位热词和上下文；具体模型负责把这些信息转换为
 * Qwen Audio 的即时热词，或 Paraformer 的预编译热词表参数。</p>
 */
public record AsrTranscriptionContext(
        List<String> languageHints,
        Map<String, Integer> vocabulary,
        String contextText,
        String vocabularyId
) {

    private static final Pattern TECHNICAL_TERM = Pattern.compile(
            "(?i)(?<![A-Za-z0-9])([A-Za-z][A-Za-z0-9+#./_-]{1,31})(?![A-Za-z0-9])");
    private static final int MAX_TERMS = 100;
    private static final int MAX_CONTEXT_LENGTH = 800;

    public AsrTranscriptionContext {
        languageHints = languageHints == null ? List.of("zh") : List.copyOf(languageHints);
        vocabulary = vocabulary == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(vocabulary));
    }

    public static AsrTranscriptionContext chinese() {
        return new AsrTranscriptionContext(List.of("zh"), Map.of(), null, null);
    }

    /**
     * 从面试岗位信息构造即时热词和短上下文。
     *
     * <p>只做确定性的轻量提取，不把简历或用户回答发送给 ASR。模型配置中的预编译
     * {@code vocabularyId} 仍由管理员维护，主要供 Paraformer-v2 使用。</p>
     */
    public static AsrTranscriptionContext forInterview(String positionTitle, String industry,
                                                       String jdText, String vocabularyId) {
        LinkedHashMap<String, Integer> terms = new LinkedHashMap<>();
        addTerm(terms, positionTitle, 5);
        addTerm(terms, industry, 4);
        String source = String.join(" ", safe(positionTitle), safe(industry), safe(jdText));
        Matcher matcher = TECHNICAL_TERM.matcher(source);
        while (matcher.find() && terms.size() < MAX_TERMS) {
            addTerm(terms, matcher.group(1), 4);
        }
        String context = source.strip().replaceAll("\\s+", " ");
        if (context.length() > MAX_CONTEXT_LENGTH) {
            context = context.substring(0, MAX_CONTEXT_LENGTH);
        }
        return new AsrTranscriptionContext(List.of("zh"), terms,
                context.isBlank() ? null : context, vocabularyId);
    }

    private static void addTerm(Map<String, Integer> terms, String value, int weight) {
        if (value == null || value.isBlank() || terms.size() >= MAX_TERMS) {
            return;
        }
        String normalized = value.strip().replaceAll("\\s+", " ");
        if (normalized.length() >= 2 && normalized.length() <= 32) {
            terms.putIfAbsent(normalized, weight);
        }
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
