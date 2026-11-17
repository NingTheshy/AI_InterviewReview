package com.interview.common.utils;

import java.util.regex.Pattern;

/** 仅允许系统生成的本地头像地址进入用户响应。 */
public final class AvatarPaths {
    private static final Pattern LOCAL_AVATAR = Pattern.compile("^/avatars/[0-9a-f]{32}\\.png$");

    private AvatarPaths() {}

    public static String visibleUrl(String avatar) {
        return avatar != null && LOCAL_AVATAR.matcher(avatar).matches() ? avatar : null;
    }

    public static String filename(String avatar) {
        String visible = visibleUrl(avatar);
        return visible == null ? null : visible.substring("/avatars/".length());
    }
}
