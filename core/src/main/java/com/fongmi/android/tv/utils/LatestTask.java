package com.fongmi.android.tv.utils;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;
import java.util.function.Function;

/** Executes only the latest request for one UI state. */
public final class LatestTask<T> implements AutoCloseable {

    private static final String KEY = "latest";
    private final KeyedLatestTask<T> tasks;

    public LatestTask(ExecutorService executor, TaskScheduler scheduler, Consumer<Throwable> logger) {
        this(executor, scheduler, logger, true);
    }

    public LatestTask(ExecutorService executor, TaskScheduler scheduler, Consumer<Throwable> logger, boolean ownsExecutor) {
        this.tasks = new KeyedLatestTask<>(executor, scheduler, logger, ownsExecutor);
    }

    public void submit(Callable<T> callable, long timeoutMillis, Consumer<T> success, Function<Throwable, T> fallback, Runnable onTimeout) {
        tasks.submit(KEY, callable, timeoutMillis, success, fallback, onTimeout, null);
    }

    public void cancel() {
        tasks.cancel(KEY, null);
    }

    @Override public void close() {
        tasks.close();
    }
}
