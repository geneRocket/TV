package com.fongmi.android.tv.model;

import java.util.concurrent.atomic.AtomicLong;

public final class SearchRequestToken {

    private static final AtomicLong VALUES = new AtomicLong();

    private SearchRequestToken() {
    }

    public static String next(String prefix) {
        return prefix + ":" + VALUES.incrementAndGet();
    }
}
