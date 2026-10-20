package com.interview.ai.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** AES-GCM AI API Key 加解密测试。 */
class AiConfigSecretCipherTest {

    private static final String KEY = Base64.getEncoder().encodeToString(
            "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8));

    @Test
    @DisplayName("相同明文使用随机 IV 生成不同密文且均可解密")
    void encryptUsesRandomIvAndDecrypts() {
        AiConfigSecretCipher cipher = new AiConfigSecretCipher(KEY);

        String first = cipher.encrypt("sk-sensitive-value");
        String second = cipher.encrypt("sk-sensitive-value");

        assertTrue(cipher.isEncrypted(first));
        assertNotEquals(first, second);
        assertEquals("sk-sensitive-value", cipher.decrypt(first));
        assertEquals("sk-sensitive-value", cipher.decrypt(second));
    }

    @Test
    @DisplayName("已加密值不会被重复加密")
    void encryptedValueIsIdempotent() {
        AiConfigSecretCipher cipher = new AiConfigSecretCipher(KEY);
        String encrypted = cipher.encrypt("sk-sensitive-value");

        assertEquals(encrypted, cipher.encrypt(encrypted));
    }

    @Test
    @DisplayName("错误密钥不能解密已有密文")
    void wrongKeyCannotDecrypt() {
        AiConfigSecretCipher cipher = new AiConfigSecretCipher(KEY);
        String otherKey = Base64.getEncoder().encodeToString(
                "abcdef0123456789abcdef0123456789".getBytes(StandardCharsets.UTF_8));
        AiConfigSecretCipher wrongCipher = new AiConfigSecretCipher(otherKey);
        String encrypted = cipher.encrypt("sk-sensitive-value");

        assertThrows(IllegalStateException.class, () -> wrongCipher.decrypt(encrypted));
    }

    @Test
    @DisplayName("主密钥必须是 Base64 编码的 32 字节值")
    void invalidMasterKeyIsRejected() {
        assertThrows(IllegalStateException.class, () -> new AiConfigSecretCipher("not-base64"));
        assertThrows(IllegalStateException.class, () -> new AiConfigSecretCipher(
                Base64.getEncoder().encodeToString("too-short".getBytes(StandardCharsets.UTF_8))));
    }
}
