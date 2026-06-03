package com.interview.common.utils;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;

/**
 * PDF 文件解析工具类
 * <p>
 * 使用 Apache PDFBox 提取 PDF 文件中的文本内容。
 * </p>
 */
@Slf4j
public final class PdfParser {

    private PdfParser() {
    }

    /**
     * 从文件中提取 PDF 文本
     *
     * @param file PDF 文件
     * @return 提取的文本内容
     * @throws IOException 文件读取异常
     */
    public static String extractText(File file) throws IOException {
        if (file == null || !file.exists()) {
            throw new IOException("PDF 文件不存在");
        }
        return extractTextFromFile(file);
    }

    /**
     * 从输入流中提取 PDF 文本
     *
     * @param inputStream PDF 输入流
     * @return 提取的文本内容
     * @throws IOException 文件读取异常
     */
    public static String extractText(InputStream inputStream) throws IOException {
        if (inputStream == null) {
            throw new IOException("输入流不能为空");
        }
        // PDFBox 3.x 不支持直接从 InputStream 加载，需要先读取为字节数组
        byte[] bytes = inputStream.readAllBytes();
        return extractTextFromBytes(bytes);
    }

    /**
     * 从文件路径中提取 PDF 文本
     *
     * @param filePath PDF 文件路径
     * @return 提取的文本内容
     * @throws IOException 文件读取异常
     */
    public static String extractText(String filePath) throws IOException {
        if (filePath == null || filePath.isEmpty()) {
            throw new IOException("文件路径不能为空");
        }
        File file = new File(filePath);
        return extractText(file);
    }

    private static String extractTextFromFile(File file) throws IOException {
        try (PDDocument document = Loader.loadPDF(file)) {
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(document);
            log.info("PDF 解析完成: 文件={}, 页数={}, 文本长度={}",
                    file.getName(), document.getNumberOfPages(), text.length());
            return cleanText(text);
        } catch (IOException e) {
            log.error("PDF 解析失败: 文件={}", file.getName(), e);
            throw e;
        }
    }

    private static String extractTextFromBytes(byte[] bytes) throws IOException {
        try (PDDocument document = Loader.loadPDF(bytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(document);
            log.info("PDF 解析完成: 页数={}, 文本长度={}",
                    document.getNumberOfPages(), text.length());
            return cleanText(text);
        } catch (IOException e) {
            log.error("PDF 解析失败", e);
            throw e;
        }
    }

    /**
     * 清理提取的文本
     * <ul>
     *   <li>去除多余空白行</li>
     *   <li>合并连续空格</li>
     *   <li>去除首尾空白</li>
     * </ul>
     */
    private static String cleanText(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        // 去除多余空白行（保留单个换行）
        text = text.replaceAll("[ \\t]*\\n[ \\t]*\\n[ \\t]*", "\n\n");
        // 合并连续空格
        text = text.replaceAll("[ \\t]+", " ");
        // 去除首尾空白
        return text.trim();
    }
}
