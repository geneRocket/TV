package com.fongmi.android.tv.utils;

import com.fongmi.android.tv.Constant;
import com.orhanobut.logger.Logger;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public final class ThreadPools {

    private static final String TAG = "ThreadPools";
    private static final int NETWORK_CONFIG_CONCURRENCY = Math.max(4, Math.min(8, Constant.THREAD_POOL));
    private static final ExecutorService CONFIG = newFixed("config", NETWORK_CONFIG_CONCURRENCY);
    private static final ExecutorService CONFIG_LOAD = newSingle("config-load");
    private static final ExecutorService LOADER = newFixed("loader", Math.max(2, Constant.THREAD_POOL / 2));
    private static final ExecutorService SEARCH = newFixed("search", Constant.THREAD_POOL);
    private static final ExecutorService PARSE = newFixed("parse", Constant.THREAD_POOL);
    private static final ExecutorService PRELOAD_PARSE = newFixed("preload-parse", Math.max(2, Constant.THREAD_POOL / 4));

    private ThreadPools() {
    }

    public static ExecutorService newFixed(String name, int size) {
        return create(name, Math.max(1, size), Math.max(1, size));
    }

    public static ExecutorService newSingle(String name) {
        return create(name, 1, 1);
    }

    public static ThreadFactory newThreadFactory(String name) {
        return threadFactory(name);
    }

    public static ExecutorService config() {
        return CONFIG;
    }

    /** Limits independent configuration downloads without starving playback traffic. */
    public static int networkConfigConcurrency() {
        return NETWORK_CONFIG_CONCURRENCY;
    }

    public static ExecutorService configLoad() {
        return CONFIG_LOAD;
    }

    public static ExecutorService loader() {
        return LOADER;
    }

    public static ExecutorService search() {
        return SEARCH;
    }

    public static ExecutorService parse() {
        return PARSE;
    }

    public static ExecutorService preloadParse() {
        return PRELOAD_PARSE;
    }

    public static void shutdown(ExecutorService executor) {
        if (executor != null) executor.shutdownNow();
    }

    public static void log(Throwable throwable, String message) {
        if (throwable == null || throwable instanceof InterruptedException) return;
        Logger.t(TAG).e(throwable, message);
    }

    private static ExecutorService create(String name, int corePoolSize, int maxPoolSize) {
        LoggingThreadPoolExecutor executor = new LoggingThreadPoolExecutor(name, corePoolSize, maxPoolSize);
        executor.allowCoreThreadTimeOut(true);
        return executor;
    }

    private static final class LoggingThreadPoolExecutor extends ThreadPoolExecutor {

        LoggingThreadPoolExecutor(String name, int corePoolSize, int maxPoolSize) {
            super(corePoolSize, maxPoolSize, 30L, TimeUnit.SECONDS, new LinkedBlockingQueue<>(), threadFactory(name));
        }

        @Override
        protected void afterExecute(Runnable runnable, Throwable throwable) {
            super.afterExecute(runnable, throwable);
            Throwable error = throwable;
            if (error == null && runnable instanceof Future<?>) {
                Future<?> future = (Future<?>) runnable;
                if (future.isDone() && !future.isCancelled()) {
                    try {
                        future.get();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } catch (Exception e) {
                        error = e.getCause() == null ? e : e.getCause();
                    }
                }
            }
            log(error, "Background task failed.");
        }
    }

    private static ThreadFactory threadFactory(String name) {
        AtomicInteger number = new AtomicInteger(1);
        return runnable -> {
            Thread thread = new Thread(runnable, "tv-" + name + "-" + number.getAndIncrement());
            thread.setPriority(Thread.NORM_PRIORITY);
            thread.setUncaughtExceptionHandler((t, e) -> log(e, "Uncaught exception on " + t.getName()));
            return thread;
        };
    }
}
