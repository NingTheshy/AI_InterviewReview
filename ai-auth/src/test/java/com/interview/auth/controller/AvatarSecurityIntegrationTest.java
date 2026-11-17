package com.interview.auth.controller;

import com.interview.auth.config.JwtAuthenticationFilter;
import com.interview.auth.config.SecurityConfig;
import com.interview.auth.mapper.UserMapper;
import com.interview.auth.service.AuthService;
import com.interview.auth.service.AvatarStorageService;
import com.interview.auth.service.RbacService;
import com.interview.common.utils.JwtUtil;
import com.interview.common.utils.RedisUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.context.SecurityContextHolder;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;

/** 头像图片可公开读取，头像修改仍须登录。 */
@WebMvcTest(controllers = {AvatarController.class, AuthController.class})
@ImportAutoConfiguration(exclude = UserDetailsServiceAutoConfiguration.class)
@ContextConfiguration(classes = {
        AvatarController.class, AuthController.class, SecurityConfig.class, JwtAuthenticationFilter.class
})
class AvatarSecurityIntegrationTest {

    private static final String NAME = "0123456789abcdef0123456789abcdef.png";

    @Autowired MockMvc mockMvc;
    @MockitoBean AuthService authService;
    @MockitoBean AvatarStorageService avatarStorageService;
    @MockitoBean JwtUtil jwtUtil;
    @MockitoBean RedisUtil redisUtil;
    @MockitoBean UserMapper userMapper;
    @MockitoBean RbacService rbacService;
    @TempDir Path directory;

    @BeforeEach
    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void anonymousUserCanReadAvatar() throws Exception {
        Path image = directory.resolve(NAME);
        Files.write(image, new byte[]{1, 2, 3});
        when(avatarStorageService.resolvePublicFile(NAME)).thenReturn(image);

        mockMvc.perform(get("/avatars/" + NAME)).andExpect(status().isOk());
    }

    @Test
    void anonymousUserCannotUploadAvatar() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "photo.png", "image/png", new byte[]{1});
        mockMvc.perform(multipart("/auth/profile/avatar").file(file).with(anonymous()))
                .andExpect(status().isUnauthorized());
    }
}
