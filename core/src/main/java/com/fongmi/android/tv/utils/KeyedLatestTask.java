package com.fongmi.android.tv.utils;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;

/** Executes the newest task for each key while allowing unrelated keys to run concurrently. */
public final class KeyedLatestTask<T> implements AutoCloseable {

    private final Object lock = new Object();
    private final ExecutorService executor;
    private final TaskScheduler scheduler;
    private final Consumer<Throwable> logger;
    private final boolean ownsExecutor;
    private final Map<String, Task> active = new HashMap<>();
    private boolean closed;

    public KeyedLatestTask(ExecutorService executor, TaskScheduler scheduler, Consumer<Throwable> logger) {
        this(executor, scheduler, logger, true);
    }

    public KeyedLatestTask(ExecutorService executor, TaskScheduler scheduler, Consumer<Throwable> logger, boolean ownsExecutor) {
        this.executor = executor;
        this.scheduler = scheduler;
        this.logger = logger;
        this.ownsExecutor = ownsExecutor;
    }

    public void submit(String key, Callable<T> callable, long timeoutMillis, Consumer<T> success, Function<Throwable, T> fallback, Runnable onTimeout, Runnable onReplace) {
        Task task = new Task(key);
        task.timeout = () -> timeout(task, fallback, success, onTimeout);
        Runnable replaced = null;
        synchronized (lock) {
            if (closed) return;
            if (cancelLocked(key)) replaced = onReplace;
            active.put(key, task);
        }
        if (replaced != null) replaced.run();
        scheduler.post(task.timeout, timeoutMillis);
        try {
            task.future = executor.submit(() -> run(task, callable, fallback, success));
            if (task.completed.get()) cancelFuture(task.future);
        } catch (RejectedExecutionException error) {
            if (complete(task)) success.accept(fallback.apply(error));
        }
    }

    public void cancel(String key, Runnable onCancel) {
        boolean cancelled;
        synchronized (lock) { cancelled = cancelLocked(key); }
        if (cancelled && onCancel != null) onCancel.run();
    }

    @Override public void close() {
        synchronized (lock) {
            closed = true;
            for (String key : active.keySet().toArray(new String[0])) cancelLocked(key);
        }
        if (ownsExecutor) executor.shutdownNow();
    }

    private void run(Task task, Callable<T> callable, Function<Throwable, T> fallback, Consumer<T> success) {
        if (task.completed.get()) return;
        try {
            T value = callable.call();
            if (complete(task)) success.accept(value);
        } catch (Throwable error) {
            if (error instanceof InterruptedException) Thread.currentThread().interrupt();
            if (complete(task)) {
                success.accept(fallback.apply(error));
                logger.accept(error);
            }
        }
    }

    private void timeout(Task task, Function<Throwable, T> fallback, Consumer<T> success, Runnable onTimeout) {
        if (!complete(task)) return;
        cancelFuture(task.future);
        if (onTimeout != null) onTimeout.run();
        success.accept(fallback.apply(new java.util.concurrent.TimeoutException()));
    }

    private boolean complete(Task task) {
        if (!task.completed.compareAndSet(false, true)) return false;
        synchronized (lock) {
            if (active.get(task.key) != task) return false;
            active.remove(task.key);
        }
        scheduler.remove(task.timeout);
        return true;
    }

    private boolean cancelLocked(String key) {
        Task task = active.remove(key);
        if (task == null || !task.completed.compareAndSet(false, true)) return false;
        scheduler.remove(task.timeout);
        cancelFuture(task.future);
        return true;
    }

    private void cancelFuture(Future<?> future) {
        if (future == null) return;
        future.cancel(true);
        if (executor instanceof ThreadPoolExecutor && future instanceof Runnable) {
            ((ThreadPoolExecutor) executor).remove((Runnable) future);
        }
    }

    private final class Task {
        private final String key;
        private Future<?> future;
        private Runnable timeout;
        private final AtomicBoolean completed = new AtomicBoolean(false);

        private Task(String key) { this.key = key; }
    }
}
