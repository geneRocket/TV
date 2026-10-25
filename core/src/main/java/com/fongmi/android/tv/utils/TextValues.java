package com.fongmi.android.tv.utils;

/** Null-safe value checks for models and business code, independent of Android UI APIs. */
public final class TextValues {
    private TextValues() {}
    public static boolean isEmpty(CharSequence value) { return value == null || value.length() == 0; }
}
