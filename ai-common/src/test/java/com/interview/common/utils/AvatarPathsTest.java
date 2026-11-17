package com.interview.common.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AvatarPathsTest {

    @Test
    void externalOrMalformedAvatarIsNotExposed() {
        String local = "/avatars/0123456789abcdef0123456789abcdef.png";
        assertEquals(local, AvatarPaths.visibleUrl(local));
        assertEquals("0123456789abcdef0123456789abcdef.png", AvatarPaths.filename(local));
        assertNull(AvatarPaths.visibleUrl("https://example.com/avatar.jpg"));
        assertNull(AvatarPaths.visibleUrl("/avatars/../secret.png"));
        assertNull(AvatarPaths.visibleUrl(null));
    }
}
