package com.interview.auth.service;

import com.interview.common.constant.ErrorCode;
import com.interview.common.exception.BusinessException;
import com.interview.common.utils.AvatarPaths;
import lombok.extern.slf4j.Slf4j;
import net.coobird.thumbnailator.Thumbnails;
import net.coobird.thumbnailator.geometry.Positions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Iterator;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/** 本地头像的校验、统一裁剪和隔离存储。 */
@Slf4j
@Service
public class AvatarStorageService {

    private static final long MAX_FILE_SIZE = 5L * 1024 * 1024;
    private static final long MAX_PIXELS = 20_000_000L;
    private static final int MAX_SIDE = 8_000;
    private static final Set<String> IMAGE_TYPES = Set.of("jpeg", "png");

    @Value("${interview.file.upload-dir:./uploads}")
    private String uploadDir;

    /** 校验真实格式和解码尺寸，输出不含原始元数据的 256×256 PNG。 */
    public String store(MultipartFile file) {
        validate(file);
        Path directory = avatarDirectory();
        Path temporary = null;
        try {
            Files.createDirectories(directory);
            if (Files.isSymbolicLink(directory)) {
                throw new IOException("头像目录不可为符号链接");
            }
            String filename = UUID.randomUUID().toString().replace("-", "") + ".png";
            Path destination = directory.resolve(filename);
            temporary = Files.createTempFile(directory, "avatar-", ".tmp");
            byte[] normalized;
            try (InputStream input = file.getInputStream(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                try {
                    Thumbnails.of(input).size(256, 256).crop(Positions.CENTER)
                            .useExifOrientation(true).outputFormat("png").toOutputStream(output);
                } catch (IOException | IllegalArgumentException ex) {
                    throw invalidImage();
                }
                normalized = output.toByteArray();
            }
            Files.write(temporary, normalized);
            try {
                Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, destination);
            }
            return "/avatars/" + filename;
        } catch (IOException | RuntimeException ex) {
            deleteFileQuietly(temporary);
            if (ex instanceof BusinessException businessException) throw businessException;
            log.warn("头像保存失败", ex);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR.getCode(), "头像保存失败，请稍后重试");
        }
    }

    /** 只允许读取本服务生成的 PNG 文件。 */
    public Path resolvePublicFile(String filename) {
        if (AvatarPaths.filename("/avatars/" + filename) == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        Path file = avatarDirectory().resolve(filename).normalize();
        if (!file.startsWith(avatarDirectory()) || !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return file;
    }

    public void deleteStoredAvatar(String avatar) {
        String filename = AvatarPaths.filename(avatar);
        if (filename != null) deleteFileQuietly(avatarDirectory().resolve(filename));
    }

    private void validate(MultipartFile file) {
        if (file == null || file.isEmpty() || file.getSize() > MAX_FILE_SIZE) {
            throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(), "请选择不超过 5 MB 的 JPG 或 PNG 图片");
        }
        String name = file.getOriginalFilename();
        String extension = name == null ? "" : name.substring(Math.max(0, name.lastIndexOf('.'))).toLowerCase(Locale.ROOT);
        boolean jpeg = (".jpg".equals(extension) || ".jpeg".equals(extension))
                && "image/jpeg".equalsIgnoreCase(file.getContentType());
        boolean png = ".png".equals(extension) && "image/png".equalsIgnoreCase(file.getContentType());
        if (!jpeg && !png) throw invalidImage();
        try (InputStream input = file.getInputStream();
             ImageInputStream image = ImageIO.createImageInputStream(input)) {
            if (image == null) throw invalidImage();
            Iterator<ImageReader> readers = ImageIO.getImageReaders(image);
            if (!readers.hasNext()) throw invalidImage();
            ImageReader reader = readers.next();
            try {
                reader.setInput(image);
                String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                if (!IMAGE_TYPES.contains(format) || (jpeg && !"jpeg".equals(format))
                        || (png && !"png".equals(format))) throw invalidImage();
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width < 1 || height < 1 || width > MAX_SIDE || height > MAX_SIDE
                        || (long) width * height > MAX_PIXELS) throw invalidImage();
            } finally {
                reader.dispose();
            }
        } catch (IOException ex) {
            throw invalidImage();
        }
    }

    private Path avatarDirectory() {
        return Path.of(uploadDir).toAbsolutePath().normalize().resolve("avatars");
    }

    private BusinessException invalidImage() {
        return new BusinessException(ErrorCode.BAD_REQUEST.getCode(), "头像只支持有效的 JPG/PNG 图片，且尺寸不能过大");
    }

    private void deleteFileQuietly(Path file) {
        if (file == null) return;
        try {
            Files.deleteIfExists(file);
        } catch (IOException ex) {
            log.warn("头像文件清理失败: {}", file, ex);
        }
    }
}
