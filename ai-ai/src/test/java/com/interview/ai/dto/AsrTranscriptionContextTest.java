package com.interview.ai.dto;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AsrTranscriptionContextTest {

    @Test
    void extractsPositionAndTechnicalTermsWithoutResumeContent() {
        AsrTranscriptionContext context = AsrTranscriptionContext.forInterview(
                "Java 后端工程师", "互联网", "熟悉 Spring Boot、Redis、Kafka 和 Kubernetes", null);

        assertEquals(5, context.vocabulary().get("Java 后端工程师"));
        assertTrue(context.vocabulary().containsKey("Spring"));
        assertTrue(context.vocabulary().containsKey("Redis"));
        assertTrue(context.contextText().contains("Kubernetes"));
        assertEquals(1, context.languageHints().size());
    }
}
