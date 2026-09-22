package com.interview.interview.controller;

import com.interview.common.utils.SecurityUtils;
import com.interview.interview.service.FileService;
import com.interview.interview.service.FileService.FileDownload;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

/**
 * 文件流式下载入口。
 *
 * <p>{@code /files/**} 面向已登录的文件所有者，{@code /asr-files/**} 面向无法携带 JWT 的 Fun-ASR。
 * 两者的授权与路径校验均下沉到 {@link FileService}，控制器只负责转换为 HTTP Range 响应。</p>
 */
@RestController
@RequiredArgsConstructor
public class FileController {

    private final FileService fileService;

    /** 返回当前用户拥有的音频或简历文件，支持单区间断点读取。 */
    @GetMapping("/files/{filename:.+}")
    public ResponseEntity<StreamingResponseBody> serveFile(
            @PathVariable String filename,
            @RequestHeader(value = "Range", required = false) String rangeHeader) {
        return toResponse(fileService.prepareUserDownload(filename,
                SecurityUtils.getCurrentUserId(), rangeHeader));
    }

    /** 返回短时 HMAC 签名授权的完整音频，供 DashScope 拉取转写源文件。 */
    @GetMapping("/asr-files/{filename:.+}")
    public ResponseEntity<StreamingResponseBody> serveAsrFile(
            @PathVariable String filename,
            @RequestParam long expires,
            @RequestParam String token) {
        return toResponse(fileService.prepareAsrDownload(filename, expires, token));
    }

    /** 将已授权的文件描述转换为 RFC 7233 单区间流式响应。 */
    private ResponseEntity<StreamingResponseBody> toResponse(FileDownload download) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(download.contentType());
        headers.setContentLength(download.length());
        headers.set(HttpHeaders.ACCEPT_RANGES, "bytes");
        headers.set(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + download.filename() + "\"");
        if (download.partial()) {
            headers.set(HttpHeaders.CONTENT_RANGE, "bytes " + download.start() + "-"
                    + download.end() + "/" + download.fileSize());
        }
        StreamingResponseBody body = output -> fileService.copy(download, output);
        return new ResponseEntity<>(body, headers,
                download.partial() ? HttpStatus.PARTIAL_CONTENT : HttpStatus.OK);
    }
}
