package com.interview.common.utils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

/**
 * Fun-ASR 拉取音频时使用的短时签名工具。
 *
 * <p>签名仅授权单个文件在有限时间内被下载，避免将用户音频永久公开。</p>
 */
public final class AsrFileUrlSigner {

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private AsrFileUrlSigner() {
    }

    /**
     * 为指定文件及过期时刻生成 URL 安全的 HMAC 签名。
     *
     * <p>文件名和过期时间均纳入签名载荷，因此签名不能被替换到其他文件或延长有效期。</p>
     *
     * @param filename 受保护的上传文件名
     * @param expiresAtEpochSecond Unix 过期时间戳（秒）
     * @param secret 服务端签名密钥
     * @return 不带填充字符的 URL 安全 Base64 签名
     */
    public static String sign(String filename, long expiresAtEpochSecond, String secret) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(
                hmac(payload(filename, expiresAtEpochSecond), secret));
    }

    /**
     * 验证签名格式、有效期和 HMAC 是否同时匹配。
     *
     * <p>使用 {@link MessageDigest#isEqual(byte[], byte[])} 比较字节数组，避免普通字符串比较
     * 泄露与首个不匹配字节位置相关的时序信息。</p>
     *
     * @return 签名有效时返回 {@code true}
     */
    public static boolean verify(String filename, long expiresAtEpochSecond, String token, String secret) {
        if (token == null || token.isBlank() || secret == null || secret.isBlank()
                || expiresAtEpochSecond < System.currentTimeMillis() / 1000) {
            return false;
        }
        byte[] expected = hmac(payload(filename, expiresAtEpochSecond), secret);
        try {
            byte[] actual = Base64.getUrlDecoder().decode(token);
            return MessageDigest.isEqual(expected, actual);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /** 计算签名载荷的 HMAC-SHA-256 摘要。 */
    private static byte[] hmac(String payload, String secret) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            return mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("无法生成 ASR 文件访问签名", e);
        }
    }

    /** 将文件名与到期时间按固定顺序编码为不可歧义的签名载荷。 */
    private static String payload(String filename, long expiresAtEpochSecond) {
        return filename + "\n" + expiresAtEpochSecond;
    }
}
