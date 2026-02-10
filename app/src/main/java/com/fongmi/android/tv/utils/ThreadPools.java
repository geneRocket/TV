package com.fongmi.android.tv.utils;

import com.fongmi.android.tv.Constant;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class ThreadPools {

    private static final ExecutorService SEARCH = Executors.newFixedThreadPool(Constant.THREAD_POOL);
    private static final ExecutorService PARSE = Executors.newFixedThreadPool(Constant.THREAD_POOL);

    private ThreadPools() {
    }

    public static ExecutorService search() {
        return SEARCH;
    }

    public static ExecutorService parse() {
        return PARSE;
    }
}
