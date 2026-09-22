package com.interview.common.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AsrFileUrlSignerTest {

    private static final String SECRET = "a-test-secret-with-more-than-thirty-two-characters";

    @Test
    void verify_acceptsCurrentSignatureForTheSameFile() {
        long expiresAt = System.currentTimeMillis() / 1000 + 60;
        String token = AsrFileUrlSigner.sign("audio.wav", expiresAt, SECRET);

        assertTrue(AsrFileUrlSigner.verify("audio.wav", expiresAt, token, SECRET));
    }

    @Test
    void verify_rejectsDifferentFileAndExpiredUrl() {
        long expiresAt = System.currentTimeMillis() / 1000 + 60;
        String token = AsrFileUrlSigner.sign("audio.wav", expiresAt, SECRET);

        assertFalse(AsrFileUrlSigner.verify("another.wav", expiresAt, token, SECRET));
        assertFalse(AsrFileUrlSigner.verify("audio.wav", expiresAt - 120, token, SECRET));
    }
}
