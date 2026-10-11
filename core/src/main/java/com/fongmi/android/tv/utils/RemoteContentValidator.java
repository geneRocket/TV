package com.fongmi.android.tv.utils;

import java.util.Locale;

/** Rejects HTML error/challenge documents where a script or configuration was expected. */
public final class RemoteContentValidator {
    private RemoteContentValidator() { }

    public static boolean isHtml(String content) {
        if (content == null) return false;
        String text = content.trim();
        if (text.startsWith("\uFEFF")) text = text.substring(1).trim();
        String prefix = text.substring(0, Math.min(text.length(), 256)).toLowerCase(Locale.ROOT);
        return prefix.startsWith("<!doctype html") || prefix.startsWith("<html")
                || prefix.startsWith("<head") || prefix.startsWith("<body");
    }
}
