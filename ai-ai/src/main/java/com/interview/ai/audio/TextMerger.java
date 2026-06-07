package com.interview.ai.audio;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 文本合并去重器
 * <p>
 * 将多个片段的 ASR 转录文本按 seq 排序合并，
 * 利用重叠窗口去除相邻片段之间的重复内容。
 * </p>
 */
@Slf4j
@Component
public class TextMerger {

    /**
     * 合并多个转录文本
     *
     * @param results         seq -> text 的映射
     * @param overlapDuration 重叠窗口时长（秒）
     * @return 合并后的完整文本
     */
    public String merge(Map<Integer, String> results, double overlapDuration) {
        if (results == null || results.isEmpty()) {
            log.warn("没有可合并的转录文本");
            return "";
        }

        if (results.size() == 1) {
            return results.values().iterator().next();
        }

        // 检测缺失的片段
        int maxSeq = results.keySet().stream().mapToInt(Integer::intValue).max().orElse(0);
        int expectedCount = maxSeq + 1;
        List<Integer> missingSeqs = new ArrayList<>();
        for (int s = 0; s <= maxSeq; s++) {
            if (!results.containsKey(s) || results.get(s) == null || results.get(s).isBlank()) {
                missingSeqs.add(s);
            }
        }
        if (!missingSeqs.isEmpty()) {
            log.warn("转录结果缺失 {} 个片段（总共 {} 个）: {}，这些内容将丢失！",
                    missingSeqs.size(), expectedCount, missingSeqs);
        }

        log.info("开始合并 {} 个片段的转录文本（共 {} 个，缺失 {} 个），重叠窗口={}s",
                results.size(), expectedCount, missingSeqs.size(), overlapDuration);

        // 估算重叠窗口对应的字符数（中文约 3-4 字/秒）
        int overlapChars = (int) (overlapDuration * 4) + 5;

        StringBuilder merged = new StringBuilder();
        String prevText = null;

        for (Map.Entry<Integer, String> entry : results.entrySet()) {
            int seq = entry.getKey();
            String currentText = entry.getValue();

            if (currentText == null || currentText.isEmpty()) {
                log.warn("片段 {} 转录文本为空，跳过", seq);
                continue;
            }

            if (prevText == null) {
                // 第一个片段，直接添加
                merged.append(currentText);
                log.debug("片段 {}: 直接添加 ({} 字符)", seq, currentText.length());
            } else {
                // 后续片段，去除与前一片段尾部的重叠
                int beforeLen = currentText.length();
                String trimmed = removeOverlap(prevText, currentText, overlapChars);
                int removed = beforeLen - trimmed.length();

                if (removed > 0) {
                    log.info("片段 {}: 去除重叠 {} 字符 (前: ...{}, 后: {}...)",
                            seq, removed,
                            prevText.substring(Math.max(0, prevText.length() - 20)),
                            trimmed.substring(0, Math.min(20, trimmed.length())));
                } else {
                    log.debug("片段 {}: 未检测到重叠，直接拼接", seq);
                }

                merged.append(trimmed);
            }
            prevText = currentText;
        }

        String result = merged.toString();
        log.info("文本合并完成: 总长度={} 字符", result.length());
        return result;
    }

    /**
     * 去除相邻文本的重叠部分
     * <p>
     * 策略：在当前片段开头处，用滑动窗口从长到短匹配前一片段尾部。
     * 只在 position=0 处匹配，避免误裁剪中间内容。
     * </p>
     */
    private String removeOverlap(String prevText, String currentText, int overlapChars) {
        if (prevText == null || currentText == null) {
            return currentText;
        }

        // 搜索窗口大小：从 overlapChars 递减到 5
        int maxWindow = Math.min(overlapChars, currentText.length());

        for (int windowLen = maxWindow; windowLen >= 5; windowLen--) {
            String prevSuffix = prevText.substring(Math.max(0, prevText.length() - windowLen));
            String currentPrefix = currentText.substring(0, Math.min(windowLen, currentText.length()));

            // 精确匹配
            if (prevSuffix.equals(currentPrefix)) {
                String trimmed = currentText.substring(windowLen);
                log.debug("精确重叠匹配: window={}, 裁剪 {} 字符", windowLen, windowLen);
                return trimmed;
            }

            // 模糊匹配：允许少量 ASR 识别差异
            int diffCount = countDifferences(prevSuffix, currentPrefix);
            if (diffCount <= windowLen * 0.15 && windowLen >= 8) {
                // 裁剪点 = 窗口长度 - 差异数（保守裁剪，避免误删）
                int trimPoint = windowLen - diffCount;
                String trimmed = currentText.substring(trimPoint);
                log.debug("模糊重叠匹配: window={}, diffs={}, 裁剪 {} 字符", windowLen, diffCount, trimPoint);
                return trimmed;
            }
        }

        return currentText;
    }

    /**
     * 计算两个等长字符串的字符差异数
     */
    private int countDifferences(String s1, String s2) {
        int diff = 0;
        int len = Math.min(s1.length(), s2.length());
        for (int i = 0; i < len; i++) {
            if (s1.charAt(i) != s2.charAt(i)) {
                diff++;
            }
        }
        return diff + Math.abs(s1.length() - s2.length());
    }
}
