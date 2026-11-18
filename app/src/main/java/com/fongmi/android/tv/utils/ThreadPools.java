package com.fongmi.android.tv.utils;

import android.os.Process;

import com.orhanobut.logger.Logger;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public final class ThreadPools {

    private static final String TAG = "ThreadPools";
    private static final ConcurrencyBudget BUDGET = ConcurrencyBudget.current();
    private static final ExecutorService GENERAL = createQueued("general", BUDGET.general);
    private static final ExecutorService CONFIG = createQueued("config", BUDGET.config);
    private static final ExecutorService CONFIG_REFRESH = createQueued("config-refresh", 2);
    private static final ExecutorService CONFIG_LOAD = createQueued("config-load", 1);
    private static final ExecutorService LIVE_CONFIG_LOAD = createQueued("config-load", 1);
    private static final ExecutorService LOADER = createQueued("loader", BUDGET.loader);
    private static final ExecutorService RECOMMEND = createQueued("recommend", 1);
    private static final ExecutorService SEARCH = createQueued("search", BUDGET.search);
    private static final ExecutorService SITE_SEARCH = createQueued("site-search", BUDGET.search);
    private static final ExecutorService PARSE = createQueued("parse", BUDGET.parse);
    private static final ExecutorService PRELOAD_PARSE = createQueued("preload-parse", BUDGET.preload);
    private static final ConcurrentHashMap<String, LatestFutureTask> CONFIG_TASKS = new ConcurrentHashMap<>();

    private ThreadPools() {
    }

    public static ExecutorService newFixed(String name, int size) {
        return createQueued(name, size);
    }

    public static ExecutorService newFixedQueued(String name, int size) {
        return createQueued(name, size);
    }

    public static ExecutorService newSingle(String name) {
        return createQueued(name, 1);
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

    /** Queues work on the owning executor so overload cannot escape its concurrency budget. */
    public static boolean executeGeneral(Runnable task) {
        try {
            GENERAL.execute(task);
            return true;
        } catch (java.util.concurrent.RejectedExecutionException error) {
            log(error, "General executor is shut down; task was rejected.");
            return false;
        }
    }

    public static ExecutorService configRefresh() {
        return CONFIG_REFRESH;
    }

    public static ExecutorService configLoad(String key) {
        return "live-config".equals(key) ? LIVE_CONFIG_LOAD : CONFIG_LOAD;
    }

    public static void executeLatestConfig(String key, Runnable task) {
        ExecutorService executor = configLoad(key);
        LatestFutureTask next = new LatestFutureTask(key, task);
        LatestFutureTask previous = CONFIG_TASKS.put(key, next);
        cancel(executor, previous);
        try {
            executor.execute(next);
        } catch (java.util.concurrent.RejectedExecutionException error) {
            CONFIG_TASKS.remove(key, next);
            throw error;
        }
    }

    public static ExecutorService loader() {
        return LOADER;
    }

    public static ExecutorService search() {
        return SEARCH;
    }

    /** Bulk searches share a budget without occupying foreground detail workers. */
    public static ExecutorService siteSearch() { return SITE_SEARCH; }

    public static ExecutorService parse() {
        return PARSE;
    }

    public static ExecutorService recommendations() { return RECOMMEND; }

    public static ExecutorService preloadParse() {
        return PRELOAD_PARSE;
    }

    public static void shutdown(ExecutorService executor) {
        if (executor != null) executor.shutdownNow();
    }

    public static void cancel(ExecutorService executor, Future<?> future) {
        if (future == null) return;
        future.cancel(true);
        if (executor instanceof ThreadPoolExecutor && future instanceof Runnable) {
            ((ThreadPoolExecutor) executor).remove((Runnable) future);
        }
    }

    public static void log(Throwable throwable, String message) {
        if (throwable == null || throwable instanceof InterruptedException) return;
        Logger.t(TAG).e(throwable, message);
    }

    private static ExecutorService createQueued(String name, int poolSize) {
        LoggingThreadPoolExecutor executor = new LoggingThreadPoolExecutor(name, Math.max(1, poolSize));
        executor.allowCoreThreadTimeOut(true);
        return executor;
    }

    private static final class LoggingThreadPoolExecutor extends ThreadPoolExecutor {

        LoggingThreadPoolExecutor(String name, int poolSize) {
            super(poolSize, poolSize, 30L, TimeUnit.SECONDS, new LinkedBlockingQueue<>(), threadFactory(name), new AbortPolicy());
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

    private static final class LatestFutureTask extends FutureTask<Void> {

        private final String key;

        LatestFutureTask(String key, Runnable task) {
            super(task, null);
            this.key = key;
        }

        @Override
        protected void done() {
            CONFIG_TASKS.remove(key, this);
        }
    }

    private static ThreadFactory threadFactory(String name) {
        AtomicInteger number = new AtomicInteger(1);
        return runnable -> {
            Runnable prioritized = () -> {
                Process.setThreadPriority(threadPriority(name));
                runnable.run();
            };
            Thread thread = new Thread(prioritized, "tv-" + name + "-" + number.getAndIncrement());
            thread.setPriority(Thread.NORM_PRIORITY);
            thread.setUncaughtExceptionHandler((t, e) -> log(e, "Uncaught exception on " + t.getName()));
            return thread;
        };
    }

    private static int threadPriority(String name) {
        if ("vod-resolver".equals(name) || "config-load".equals(name) || "config".equals(name) || "parse".equals(name) || "parse-job".equals(name) || name.endsWith("-playback") || name.endsWith("-url")) return Process.THREAD_PRIORITY_DEFAULT;
        return Process.THREAD_PRIORITY_BACKGROUND;
    }
}
