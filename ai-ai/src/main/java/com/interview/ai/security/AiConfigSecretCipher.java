package com.interview.ai.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AI 配置密钥加密器。
 *
 * <p>使用 AES-256-GCM 加密数据库中的 API Key。每次加密生成独立的 96 位随机 IV，
 * 密文携带版本前缀，便于后续轮换算法。未带前缀的值只作为旧数据兼容读取，启动迁移器
 * 会将其转换成密文。</p>
 */
@Component
public class AiConfigSecretCipher {

    static final String PREFIX = "enc:v1:";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int KEY_LENGTH_BYTES = 32;
    private static final int IV_LENGTH_BYTES = 12;
    private static final int TAG_LENGTH_BITS = 128;

    private final SecretKeySpec key;
    private final SecureRandom secureRandom = new SecureRandom();

    public AiConfigSecretCipher(
            @Value("${interview.security.ai-config-encryption-key}") String encodedKey) {
        byte[] keyBytes;
        try {
            keyBytes = Base64.getDecoder().decode(encodedKey);
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("AI_CONFIG_ENCRYPTION_KEY 必须是 Base64 编码的 32 字节密钥", ex);
        }
        if (keyBytes.length != KEY_LENGTH_BYTES) {
            throw new IllegalStateException("AI_CONFIG_ENCRYPTION_KEY 解码后必须恰好为 32 字节");
        }
        this.key = new SecretKeySpec(keyBytes, "AES");
    }

    /** 加密明文；空值保持不变，已经加密的值不会重复加密。 */
    public String encrypt(String value) {
        if (value == null || value.isBlank() || isEncrypted(value)) {
            return value;
        }
        try {
            byte[] iv = new byte[IV_LENGTH_BYTES];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] cipherText = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
            byte[] payload = ByteBuffer.allocate(iv.length + cipherText.length)
                    .put(iv)
                    .put(cipherText)
                    .array();
            return PREFIX + Base64.getEncoder().encodeToString(payload);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("AI API Key 加密失败", ex);
        }
    }

    /** 解密密文；未带版本前缀的旧值按明文返回，供一次性迁移使用。 */
    public String decrypt(String value) {
        if (value == null || value.isBlank() || !isEncrypted(value)) {
            return value;
        }
        try {
            byte[] payload = Base64.getDecoder().decode(value.substring(PREFIX.length()));
            if (payload.length <= IV_LENGTH_BYTES) {
                throw new IllegalStateException("AI API Key 密文格式无效");
            }
            ByteBuffer buffer = ByteBuffer.wrap(payload);
            byte[] iv = new byte[IV_LENGTH_BYTES];
            buffer.get(iv);
            byte[] cipherText = new byte[buffer.remaining()];
            buffer.get(cipherText);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            return new String(cipher.doFinal(cipherText), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException ex) {
            throw new IllegalStateException("AI API Key 解密失败，请检查加密密钥是否正确", ex);
        }
    }

    /** 判断数据库值是否已经使用当前版本格式加密。 */
    public boolean isEncrypted(String value) {
        return value != null && value.startsWith(PREFIX);
    }
}
