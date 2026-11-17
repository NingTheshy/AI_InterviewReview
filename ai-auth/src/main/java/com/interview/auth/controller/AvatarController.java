package com.interview.auth.controller;

import com.interview.auth.service.AvatarStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.net.MalformedURLException;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

/** 公开读取系统生成的头像，供面经和评论页面展示。 */
@RestController
@RequiredArgsConstructor
public class AvatarController {

    private final AvatarStorageService avatarStorageService;

    @GetMapping("/avatars/{filename:.+}")
    public ResponseEntity<Resource> getAvatar(@PathVariable String filename) throws MalformedURLException {
        Path file = avatarStorageService.resolvePublicFile(filename);
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .cacheControl(CacheControl.maxAge(365, TimeUnit.DAYS).cachePublic().immutable())
                .header("X-Content-Type-Options", "nosniff")
                .body(new UrlResource(file.toUri()));
    }
}
