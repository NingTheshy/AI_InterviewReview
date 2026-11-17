package com.interview.ai.model.dashscope;

import com.interview.ai.config.AiProviderProperties;
import com.interview.ai.config.DashScopeAsrProperties;
import com.interview.ai.dto.AsrTranscriptionContext;
import com.interview.common.exception.BusinessException;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.audio.transcription.AudioTranscriptionPrompt;
import org.springframework.ai.audio.transcription.TranscriptionModel;
import org.springframework.core.io.FileSystemResource;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DashScopeFileTranscriptionModelTest {

    @TempDir
    Path tempDir;

    @Test
    void implementsSpringAiTranscriptionModelAndPreservesDurationPolicy() {
        DashScopeAsrProperties properties = validAsrProperties();
        properties.setMaxDiarizationDurationMinutes(120);
        DashScopeFileTranscriptionModel model = new DashScopeFileTranscriptionModel(providerProperties(), properties);

        assertInstanceOf(TranscriptionModel.class, model);
        assertEquals(Duration.ofHours(2), model.maxRecommendedDuration());
    }

    @Test
    void rejectsMissingPublicUrlBeforeRemoteCall() throws Exception {
        Path audio = tempDir.resolve("interview.wav");
        Files.write(audio, new byte[]{1});
        DashScopeAsrProperties properties = validAsrProperties();
        properties.setFileSourceMode("SIGNED_PUBLIC_URL");
        properties.setPublicBaseUrl("");
        DashScopeFileTranscriptionModel model = new DashScopeFileTranscriptionModel(providerProperties(), properties);

        assertThrows(BusinessException.class, () -> model.call(new AudioTranscriptionPrompt(
                new FileSystemResource(audio), new DashScopeTranscriptionOptions(
                        DashScopeFileTranscriptionModel.QWEN_MODEL, AsrTranscriptionContext.chinese()))));
    }

    @Test
    @SuppressWarnings("unchecked")
    void qwenUsesInstantVocabularyAndPromptContext() {
        DashScopeFileTranscriptionModel model = new DashScopeFileTranscriptionModel(
                providerProperties(), validAsrProperties());
        AsrTranscriptionContext context = new AsrTranscriptionContext(
                List.of("zh"), Map.of("Spring Boot", 5), "Java 后端岗位", null);

        Map<String, Object> body = model.buildRequestBody("oss://audio.wav",
                DashScopeFileTranscriptionModel.QWEN_MODEL, context);
        Map<String, Object> input = (Map<String, Object>) body.get("input");
        Map<String, Object> parameters = (Map<String, Object>) body.get("parameters");

        assertEquals(Map.of("Spring Boot", 5), parameters.get("vocabulary"));
        assertEquals(List.of("zh"), parameters.get("language_hints"));
        assertInstanceOf(List.class, input.get("context"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void paraformerUsesBilingualHintsAndPrecompiledVocabulary() {
        DashScopeFileTranscriptionModel model = new DashScopeFileTranscriptionModel(
                providerProperties(), validAsrProperties());
        AsrTranscriptionContext context = new AsrTranscriptionContext(
                List.of("zh"), Map.of("Redis", 4), null, "vocab-123");

        Map<String, Object> body = model.buildRequestBody("oss://audio.wav",
                DashScopeFileTranscriptionModel.PARAFORMER_MODEL, context);
        Map<String, Object> parameters = (Map<String, Object>) body.get("parameters");

        assertEquals(List.of("zh", "en"), parameters.get("language_hints"));
        assertEquals("vocab-123", parameters.get("vocabulary_id"));
        assertEquals(true, parameters.get("timestamp_alignment_enabled"));
        assertEquals(null, parameters.get("vocabulary"));
    }

    @Test
    void downloadsSignedResultWithoutChangingEncodedQueryOrAddingApiKey() throws Exception {
        String signedQuery = "OSSAccessKeyId=test&Expires=1794969040&Signature=abc%2Bdef%2Fghi%3D";
        AtomicReference<String> receivedQuery = new AtomicReference<>();
        AtomicReference<String> receivedAuthorization = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/result.json", exchange -> {
            receivedQuery.set(exchange.getRequestURI().getRawQuery());
            receivedAuthorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] body = "{\"transcripts\":[]}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (var output = exchange.getResponseBody()) {
                output.write(body);
            }
        });
        server.start();
        try {
            DashScopeFileTranscriptionModel model = new DashScopeFileTranscriptionModel(
                    providerProperties(), validAsrProperties());
            String url = "http://127.0.0.1:" + server.getAddress().getPort()
                    + "/result.json?" + signedQuery;

            assertEquals("{\"transcripts\":[]}", model.downloadTranscriptionResult(url));
            assertEquals(signedQuery, receivedQuery.get());
            assertEquals(null, receivedAuthorization.get());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void reportsOssSignatureFailureWithoutExposingSignedUrl() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/result.json", exchange -> {
            byte[] body = "<Error><Code>SignatureDoesNotMatch</Code></Error>"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(403, body.length);
            try (var output = exchange.getResponseBody()) {
                output.write(body);
            }
        });
        server.start();
        try {
            DashScopeFileTranscriptionModel model = new DashScopeFileTranscriptionModel(
                    providerProperties(), validAsrProperties());
            String url = "http://127.0.0.1:" + server.getAddress().getPort()
                    + "/result.json?Signature=sensitive%2Bvalue";

            BusinessException error = assertThrows(BusinessException.class,
                    () -> model.downloadTranscriptionResult(url));
            assertTrue(error.getMessage().contains("OSS 签名校验失败"));
            assertTrue(!error.getMessage().contains("sensitive"));
        } finally {
            server.stop(0);
        }
    }

    private AiProviderProperties providerProperties() {
        AiProviderProperties properties = new AiProviderProperties();
        properties.setApiKey("test-key");
        properties.setApiEndpoint("https://dashscope.example.test/api/v1");
        properties.setModelName(DashScopeFileTranscriptionModel.QWEN_MODEL);
        properties.setTimeoutSeconds(5);
        return properties;
    }

    private DashScopeAsrProperties validAsrProperties() {
        DashScopeAsrProperties properties = new DashScopeAsrProperties();
        properties.setPublicBaseUrl("https://audio.example.test");
        properties.setFileUrlSecret("test-file-url-secret-with-sufficient-length");
        properties.setDiarizationEnabled(true);
        return properties;
    }
}
