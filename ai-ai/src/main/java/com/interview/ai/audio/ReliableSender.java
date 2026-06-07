package com.interview.ai.audio;

import com.interview.ai.config.AudioChunkProperties;
import com.interview.ai.service.AsrClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 可靠并发发送器
 * <p>
 * 模拟 TCP 的确认应答机制：并发窗口、超时重传、序列号去重。
 * 将多个音频片段并发发送给 ASR 客户端，自动重试失败的片段。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReliableSender {

    private final AudioChunkProperties chunkProperties;

    /**
     * 并发发送所有音频片段给 ASR 客户端
     *
     * @param chunks 音频片段列表
     * @param client ASR 客户端
     * @return 按 seq 排序的转录文本映射 (seq -> text)
     */
    public Map<Integer, String> sendAll(List<AudioChunk> chunks, AsrClient client) {
        int concurrency = chunkProperties.getConcurrency();
        int maxRetries = chunkProperties.getMaxRetries();
        int timeoutSeconds = chunkProperties.getTimeoutSeconds();

        log.info("开始并发 ASR 转录: {} 个片段, 并发窗口={}, 最大重试={}, 超时={}s",
                chunks.size(), concurrency, maxRetries, timeoutSeconds);

        Set<Integer> acked = ConcurrentHashMap.newKeySet();
        ConcurrentSkipListMap<Integer, String> results = new ConcurrentSkipListMap<>();
        AtomicInteger failCount = new AtomicInteger(0);
        AtomicLong totalAsrTime = new AtomicLong(0);

        Semaphore semaphore = new Semaphore(concurrency);
        ExecutorService executor = Executors.newFixedThreadPool(
                Math.min(chunks.size(), concurrency * 2));

        List<Future<?>> futures = new ArrayList<>();

        for (AudioChunk chunk : chunks) {
            Future<?> future = executor.submit(() -> {
                sendWithRetry(chunk, client, semaphore, acked, results, failCount, maxRetries, timeoutSeconds, totalAsrTime);
            });
            futures.add(future);
        }

        // 等待所有任务完成（带全局超时）
        long globalTimeoutMs = (long) timeoutSeconds * chunks.size() * (maxRetries + 1) * 1000L;
        for (Future<?> future : futures) {
            try {
                future.get(globalTimeoutMs, TimeUnit.MILLISECONDS);
            } catch (TimeoutException e) {
                log.error("ASR 发送任务全局超时 ({}ms)，强制跳过", globalTimeoutMs);
                future.cancel(true);
            } catch (InterruptedException | ExecutionException e) {
                log.error("ASR 发送任务异常", e);
            }
        }

        executor.shutdown();

        long totalSec = totalAsrTime.get() / 1000;
        if (failCount.get() > 0) {
            log.warn("ASR 转录完成: 成功={}, 失败={}, 总耗时={}s", results.size(), failCount.get(), totalSec);
        } else {
            log.info("ASR 转录全部完成: 成功={}/{}, 总耗时={}s", results.size(), chunks.size(), totalSec);
        }
        return results;
    }

    /**
     * 带重试的单片段发送
     */
    private void sendWithRetry(AudioChunk chunk, AsrClient client,
                                Semaphore semaphore, Set<Integer> acked,
                                ConcurrentSkipListMap<Integer, String> results,
                                AtomicInteger failCount, int maxRetries,
                                int timeoutSeconds, AtomicLong totalAsrTime) {
        int seq = chunk.getSeq();

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                semaphore.acquire();
                try {
                    if (acked.contains(seq)) {
                        log.debug("片段 {} 已确认，跳过", seq);
                        return;
                    }

                    MultipartFile multipartFile = new ChunkMultipartFile(
                            "chunk_" + seq + ".wav", chunk.getAudioData());

                    long estimatedBase64Size = chunk.getAudioData().length * 4L / 3;
                    if (estimatedBase64Size > 9_000_000) {
                        log.warn("片段 {} 预估 base64 大小 {}KB，可能超过 ASR 请求限制！",
                                seq, estimatedBase64Size / 1024);
                    }

                    // 带超时的 ASR 调用
                    long asrStart = System.currentTimeMillis();
                    ExecutorService singleExecutor = Executors.newSingleThreadExecutor();
                    try {
                        Future<String> asrFuture = singleExecutor.submit(() -> client.transcribe(multipartFile, "zh"));
                        String text = asrFuture.get(timeoutSeconds, TimeUnit.SECONDS);
                        long asrElapsed = System.currentTimeMillis() - asrStart;
                        totalAsrTime.addAndGet(asrElapsed);

                        if (text == null || text.isBlank()) {
                            log.warn("片段 {} ASR 返回空文本（大小={}KB, 耗时={}ms），将重试",
                                    seq, chunk.getAudioData().length / 1024, asrElapsed);
                            throw new RuntimeException("ASR 返回空文本");
                        }

                        if (acked.add(seq)) {
                            results.put(seq, text);
                            log.info("片段 {} 转录成功: {} 字符, 耗时={}ms", seq, text.length(), asrElapsed);
                        }
                        return;
                    } finally {
                        singleExecutor.shutdownNow();
                    }

                } finally {
                    semaphore.release();
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("片段 {} 被中断", seq);
                failCount.incrementAndGet();
                return;

            } catch (TimeoutException e) {
                log.warn("片段 {} 第 {} 次尝试超时 ({}s)", seq, attempt, timeoutSeconds);
                if (attempt >= maxRetries) {
                    log.error("片段 {} 最终超时失败（已重试 {} 次），跳过此片段", seq, maxRetries);
                    failCount.incrementAndGet();
                }

            } catch (Exception e) {
                log.warn("片段 {} 第 {} 次尝试失败: {}", seq, attempt, e.getMessage());
                if (attempt >= maxRetries) {
                    log.error("片段 {} 最终失败（已重试 {} 次），跳过此片段", seq, maxRetries);
                    failCount.incrementAndGet();
                }
            }

            // 指数退避（不在最后一次尝试时等待）
            if (attempt < maxRetries) {
                try {
                    long waitMs = 2000L * attempt;
                    log.debug("片段 {} 等待 {}ms 后重试", seq, waitMs);
                    Thread.sleep(waitMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    failCount.incrementAndGet();
                    return;
                }
            }
        }
    }

    /**
     * 音频片段的 MultipartFile 实现
     */
    private static class ChunkMultipartFile implements MultipartFile {
        private final String filename;
        private final byte[] content;

        ChunkMultipartFile(String filename, byte[] content) {
            this.filename = filename;
            this.content = content;
        }

        @Override
        public String getName() { return "file"; }

        @Override
        public String getOriginalFilename() { return filename; }

        @Override
        public String getContentType() { return "audio/wav"; }

        @Override
        public boolean isEmpty() { return content.length == 0; }

        @Override
        public long getSize() { return content.length; }

        @Override
        public byte[] getBytes() { return content; }

        @Override
        public InputStream getInputStream() { return new ByteArrayInputStream(content); }

        @Override
        public void transferTo(java.io.File dest) throws IOException {
            java.nio.file.Files.write(dest.toPath(), content);
        }
    }
}
