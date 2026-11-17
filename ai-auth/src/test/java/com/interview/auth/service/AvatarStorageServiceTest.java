package com.interview.auth.service;

import com.interview.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class AvatarStorageServiceTest {

    @TempDir Path uploadDir;
    private final AvatarStorageService service = new AvatarStorageService();

    @BeforeEach
    void configureDirectory() {
        ReflectionTestUtils.setField(service, "uploadDir", uploadDir.toString());
    }

    @Test
    void cropsValidPngToSquareAndServesOnlyGeneratedFilename() throws IOException {
        String url = service.store(image("photo.png", "image/png", "png", 400, 240));
        assertTrue(url.matches("/avatars/[0-9a-f]{32}\\.png"));

        Path stored = service.resolvePublicFile(url.substring("/avatars/".length()));
        BufferedImage result = ImageIO.read(stored.toFile());
        assertEquals(256, result.getWidth());
        assertEquals(256, result.getHeight());
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ResponseStatusException.class,
                () -> service.resolvePublicFile("../photo.png")).getStatusCode());

        service.deleteStoredAvatar(url);
        assertFalse(Files.exists(stored));
    }

    @Test
    void acceptsJpegAndRejectsFakeOrOversizedImages() throws IOException {
        String url = service.store(image("portrait.jpg", "image/jpeg", "jpg", 100, 140));
        assertTrue(Files.exists(service.resolvePublicFile(url.substring("/avatars/".length()))));

        assertThrows(BusinessException.class, () -> service.store(
                new MockMultipartFile("file", "fake.jpg", "image/jpeg", "not an image".getBytes())));
        assertThrows(BusinessException.class, () -> service.store(
                new MockMultipartFile("file", "huge.png", "image/png", new byte[5 * 1024 * 1024 + 1])));
        assertThrows(BusinessException.class, () -> service.store(
                image("too-wide.png", "image/png", "png", 8001, 1)));
    }

    private MockMultipartFile image(String name, String contentType, String format,
                                    int width, int height) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        image.setRGB(0, 0, Color.BLUE.getRGB());
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(image, format, bytes);
        return new MockMultipartFile("file", name, contentType, bytes.toByteArray());
    }
}
