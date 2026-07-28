package com.fongmi.android.tv.utils;

import com.orhanobut.logger.Logger;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public final class ThreadPools {

    private static final String TAG = "ThreadPools";
    private static final ConcurrencyBudget BUDGET = ConcurrencyBudget.current();
    private static final ExecutorService GENERAL = create("general", BUDGET.general, 256, new ThreadPoolExecutor.AbortPolicy());
    private static final ExecutorService GENERAL_OVERFLOW = create("general-overflow", 1, 256, new ThreadPoolExecutor.AbortPolicy());
    private static final ExecutorService CONFIG = create("config", BUDGET.config, 128, new ThreadPoolExecutor.CallerRunsPolicy());
    private static final ExecutorService CONFIG_LOAD = create("config-load", 1, 32, new ThreadPoolExecutor.CallerRunsPolicy());
    private static final ExecutorService LOADER = create("loader", BUDGET.loader, 128, new ThreadPoolExecutor.CallerRunsPolicy());
    private static final ExecutorService SEARCH = create("search", BUDGET.search, 256, new ThreadPoolExecutor.AbortPolicy());
    private static final ExecutorService PARSE = create("parse", BUDGET.parse, 128, new ThreadPoolExecutor.CallerRunsPolicy());
    private static final ExecutorService PRELOAD_PARSE = create("preload-parse", BUDGET.preload, 64, new ThreadPoolExecutor.AbortPolicy());

    private ThreadPools() {
    }

    public static ExecutorService newFixed(String name, int size) {
        int threads = Math.max(1, size);
        return create(name, threads, Math.max(16, threads * 16), new ThreadPoolExecutor.CallerRunsPolicy());
    }

    public static ExecutorService newFixedRejecting(String name, int size, int queueCapacity) {
        return create(name, Math.max(1, size), Math.max(1, queueCapacity), new ThreadPoolExecutor.AbortPolicy());
    }

    public static ExecutorService newSingle(String name) {
        return create(name, 1, 64, new ThreadPoolExecutor.CallerRunsPolicy());
    }

    public static int searchConcurrency() {
        return BUDGET.search;
    }

    public static ThreadFactory newThreadFactory(String name) {
        return threadFactory(name);
    }

    public static ExecutorService config() {
        return CONFIG;
    }

    /** Keeps overload work off the caller thread while retaining a finite process-wide backlog. */
    public static boolean executeGeneral(Runnable task) {
        try {
            GENERAL.execute(task);
            return true;
        } catch (java.util.concurrent.RejectedExecutionException first) {
            try {
                GENERAL_OVERFLOW.execute(task);
                return true;
            } catch (java.util.concurrent.RejectedExecutionException second) {
                log(second, "General and overflow queues are full; task was rejected.");
                return false;
            }
        }
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

    private static ExecutorService create(String name, int poolSize, int queueCapacity, RejectedExecutionHandler handler) {
        LoggingThreadPoolExecutor executor = new LoggingThreadPoolExecutor(name, Math.max(1, poolSize), queueCapacity, handler);
        executor.allowCoreThreadTimeOut(true);
        return executor;
    }

    private static final class LoggingThreadPoolExecutor extends ThreadPoolExecutor {

        LoggingThreadPoolExecutor(String name, int poolSize, int queueCapacity, RejectedExecutionHandler handler) {
            super(poolSize, poolSize, 30L, TimeUnit.SECONDS, new ArrayBlockingQueue<>(queueCapacity), threadFactory(name), handler);
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
