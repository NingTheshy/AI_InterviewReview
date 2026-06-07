package com.interview.ai.audio;

import com.interview.ai.config.AudioChunkProperties;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * FFmpeg 路径智能探测器
 * <p>
 * 三级搜索策略：
 * <ol>
 *   <li>优先使用配置路径（ai.chunk.ffmpeg-path 或环境变量 FFMPEG_PATH）</li>
 *   <li>搜索系统 PATH 和常见安装目录</li>
 *   <li>使用默认值 "ffmpeg"（依赖系统 PATH）</li>
 * </ol>
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FfmpegResolver {

    private final AudioChunkProperties chunkProperties;

    /** 探测到的有效 FFmpeg 路径（缓存） */
    private volatile String resolvedPath;

    /** 是否已完成探测 */
    private volatile boolean resolved = false;

    /**
     * 获取可用的 FFmpeg 路径
     *
     * @return FFmpeg 可执行文件的绝对路径
     * @throws IllegalStateException 如果找不到可用的 FFmpeg
     */
    public String resolve() {
        if (resolved && resolvedPath != null) {
            return resolvedPath;
        }
        synchronized (this) {
            if (resolved) {
                return resolvedPath;
            }
            resolvedPath = doResolve();
            resolved = true;
            return resolvedPath;
        }
    }

    /**
     * 执行三级路径探测
     */
    private String doResolve() {
        // 第一级：配置路径
        String configured = chunkProperties.getFfmpegPath();
        if (configured != null && !configured.isBlank() && !"ffmpeg".equals(configured)) {
            if (isExecutable(configured)) {
                log.info("FFmpeg 路径（配置）: {}", configured);
                return configured;
            }
            log.warn("配置的 FFmpeg 路径不可用: {}", configured);
        }

        // 第二级：智能搜索
        String found = searchSystem();
        if (found != null) {
            log.info("FFmpeg 路径（系统搜索）: {}", found);
            return found;
        }

        // 第三级：默认值
        if (isExecutable("ffmpeg")) {
            log.info("FFmpeg 路径（系统 PATH）: ffmpeg");
            return "ffmpeg";
        }

        // 全部失败
        log.error("未找到可用的 FFmpeg。请安装 FFmpeg 并配置 ai.chunk.ffmpeg-path 或添加到系统 PATH");
        return null;
    }

    /**
     * 智能搜索系统中的 FFmpeg
     */
    private String searchSystem() {
        List<String> candidates = new ArrayList<>();

        String os = System.getProperty("os.name", "").toLowerCase();

        if (os.contains("win")) {
            // Windows 常见路径
            candidates.add("C:\\ffmpeg\\bin\\ffmpeg.exe");
            candidates.add("C:\\Program Files\\ffmpeg\\bin\\ffmpeg.exe");
            candidates.add("C:\\Program Files (x86)\\ffmpeg\\bin\\ffmpeg.exe");
            candidates.add("C:\\tools\\ffmpeg\\bin\\ffmpeg.exe");
            candidates.add("C:\\ProgramData\\chocolatey\\bin\\ffmpeg.exe");

            // 用户目录下的常见位置
            String userHome = System.getProperty("user.home");
            if (userHome != null) {
                candidates.add(userHome + "\\scoop\\apps\\ffmpeg\\current\\bin\\ffmpeg.exe");
                candidates.add(userHome + "\\AppData\\Local\\Microsoft\\WinGet\\Packages\\");
            }

            // 环境变量 PATH 中搜索
            String pathEnv = System.getenv("PATH");
            if (pathEnv != null) {
                for (String dir : pathEnv.split(";")) {
                    candidates.add(dir + "\\ffmpeg.exe");
                }
            }

        } else if (os.contains("mac")) {
            // macOS 常见路径
            candidates.add("/usr/local/bin/ffmpeg");
            candidates.add("/opt/homebrew/bin/ffmpeg");
            candidates.add("/usr/local/Cellar/ffmpeg/");

            // Homebrew 动态路径
            String brewPrefix = execCommand("brew --prefix ffmpeg 2>/dev/null");
            if (brewPrefix != null) {
                candidates.add(brewPrefix + "/bin/ffmpeg");
            }

        } else {
            // Linux 常见路径
            candidates.add("/usr/bin/ffmpeg");
            candidates.add("/usr/local/bin/ffmpeg");
            candidates.add("/snap/bin/ffmpeg");
            candidates.add("/usr/local/ffmpeg/bin/ffmpeg");

            // 环境变量 PATH 中搜索
            String pathEnv = System.getenv("PATH");
            if (pathEnv != null) {
                for (String dir : pathEnv.split(":")) {
                    candidates.add(dir + "/ffmpeg");
                }
            }
        }

        // 逐一验证候选路径
        for (String candidate : candidates) {
            if (candidate.endsWith("\\") || candidate.endsWith("/")) {
                // 目录路径，尝试拼接可执行文件名
                String exe = os.contains("win") ? "ffmpeg.exe" : "ffmpeg";
                candidate = candidate + exe;
            }
            if (isExecutable(candidate)) {
                return candidate;
            }
        }

        return null;
    }

    /**
     * 检查路径是否是可执行的 FFmpeg
     */
    private boolean isExecutable(String path) {
        try {
            // 先检查文件是否存在（绝对路径的情况）
            if (!"ffmpeg".equals(path)) {
                File file = new File(path);
                if (!file.exists() || !file.canExecute()) {
                    return false;
                }
            }

            // 执行 ffmpeg -version 验证
            Process process = new ProcessBuilder(path, "-version")
                    .redirectErrorStream(true)
                    .start();

            // 读取输出（防止进程阻塞）
            byte[] output = process.getInputStream().readAllBytes();
            int exitCode = process.waitFor();

            if (exitCode == 0) {
                String versionInfo = new String(output);
                // 确认是 ffmpeg 而非其他程序
                return versionInfo.toLowerCase().contains("ffmpeg");
            }
            return false;
        } catch (Exception e) {
            log.trace("FFmpeg 路径验证失败: {} - {}", path, e.getMessage());
            return false;
        }
    }

    /**
     * 执行命令并返回第一行输出
     */
    private String execCommand(String command) {
        try {
            Process process = new ProcessBuilder("sh", "-c", command)
                    .redirectErrorStream(true)
                    .start();
            String output = new String(process.getInputStream().readAllBytes()).trim();
            process.waitFor();
            return output.isEmpty() ? null : output;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 检查 FFmpeg 是否可用
     */
    public boolean isAvailable() {
        return resolve() != null;
    }

    /**
     * 重置缓存（用于测试或配置变更后重新探测）
     */
    public void reset() {
        resolved = false;
        resolvedPath = null;
    }
}
