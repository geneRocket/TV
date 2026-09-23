package com.fongmi.android.tv.utils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;

/** Lifecycle-bound heterogeneous task coordinator with keyed stale-result suppression. */
public final class TaskScope implements AutoCloseable {

    private final Object lock = new Object();
    private final ExecutorService executor;
    private final TaskScheduler scheduler;
    private final Consumer<Throwable> logger;
    private final boolean ownsExecutor;
    private final Map<String, Entry<?>> active = new HashMap<>();
    private boolean closed;

    public TaskScope(ExecutorService executor, TaskScheduler scheduler, Consumer<Throwable> logger) {
        this(executor, scheduler, logger, true);
    }

    public TaskScope(ExecutorService executor, TaskScheduler scheduler, Consumer<Throwable> logger, boolean ownsExecutor) {
        this.executor = executor;
        this.scheduler = scheduler;
        this.logger = logger;
        this.ownsExecutor = ownsExecutor;
    }

    public <T> void submit(String key, Callable<T> callable, long timeoutMillis, Consumer<T> success,
            Function<Throwable, T> fallback, Runnable cancelHook) {
        Entry<T> entry = new Entry<>(key, callable, success, fallback, cancelHook);
        entry.timeout = () -> timeout(entry);
        Entry<?> replaced;
        synchronized (lock) {
            if (closed) return;
            replaced = cancelLocked(key);
            active.put(key, entry);
        }
        runCancelHook(replaced);
        synchronized (lock) {
            if (closed || active.get(key) != entry) return;
            if (timeoutMillis > 0) scheduler.post(entry.timeout, timeoutMillis);
        }
        try {
            entry.future = executor.submit(() -> run(entry));
            if (entry.done.get() || entry.cancelled.get()) cancelFuture(entry.future);
        } catch (RejectedExecutionException error) {
            finishWithError(entry, error);
        }
    }

    public boolean cancel(String key) {
        Entry<?> cancelled;
        synchronized (lock) {
            cancelled = cancelLocked(key);
        }
        runCancelHook(cancelled);
        return cancelled != null;
    }

    @Override
    public void close() {
        List<Entry<?>> cancelled = new ArrayList<>();
        synchronized (lock) {
            if (closed) return;
            closed = true;
            for (String key : active.keySet().toArray(new String[0])) {
                Entry<?> entry = cancelLocked(key);
                if (entry != null) cancelled.add(entry);
            }
        }
        for (Entry<?> entry : cancelled) runCancelHook(entry);
        if (ownsExecutor) executor.shutdownNow();
    }

    private <T> void run(Entry<T> entry) {
        if (entry.cancelled.get() || entry.done.get()) return;
        try {
            T value = entry.callable.call();
            if (completeWork(entry)) dispatch(entry, value);
        } catch (Throwable error) {
            if (error instanceof InterruptedException) Thread.currentThread().interrupt();
            finishWithError(entry, error);
        }
    }

    private <T> void finishWithError(Entry<T> entry, Throwable error) {
        if (!completeWork(entry)) return;
        try {
            dispatch(entry, entry.fallback.apply(error));
        } catch (Throwable fallbackError) {
            logger.accept(fallbackError);
        }
        if (!(error instanceof InterruptedException)) logger.accept(error);
    }

    private <T> void timeout(Entry<T> entry) {
        if (!completeWork(entry)) return;
        cancelFuture(entry.future);
        if (entry.cancelHook != null) runHook(entry.cancelHook);
        try {
            dispatch(entry, entry.fallback.apply(new TimeoutException()));
        } catch (Throwable error) {
            logger.accept(error);
        }
    }

    private <T> void publish(Entry<T> entry, T value) {
        try {
            entry.success.accept(value);
        } catch (Throwable error) {
            logger.accept(error);
        }
    }

    private <T> void dispatch(Entry<T> entry, T value) {
        Runnable delivery = () -> {
            if (!completeDelivery(entry)) return;
            publish(entry, value);
        };
        synchronized (lock) {
            if (closed || active.get(entry.key) != entry) return;
            entry.delivery = delivery;
            scheduler.post(delivery, 0);
        }
    }

    private boolean completeWork(Entry<?> entry) {
        if (!entry.done.compareAndSet(false, true)) return false;
        synchronized (lock) {
            if (active.get(entry.key) != entry) return false;
        }
        if (entry.timeout != null) scheduler.remove(entry.timeout);
        return true;
    }

    private boolean completeDelivery(Entry<?> entry) {
        synchronized (lock) {
            if (active.get(entry.key) != entry || entry.cancelled.get()) return false;
            active.remove(entry.key);
        }
        return true;
    }

    private Entry<?> cancelLocked(String key) {
        Entry<?> entry = active.remove(key);
        if (entry == null || !entry.cancelled.compareAndSet(false, true)) return null;
        if (entry.timeout != null) scheduler.remove(entry.timeout);
        if (entry.delivery != null) scheduler.remove(entry.delivery);
        cancelFuture(entry.future);
        return entry;
    }

    private void cancelFuture(Future<?> future) {
        if (future == null) return;
        future.cancel(true);
        if (executor instanceof ThreadPoolExecutor && future instanceof Runnable) {
            ((ThreadPoolExecutor) executor).remove((Runnable) future);
        }
    }

    private void runHook(Runnable hook) {
        try {
            hook.run();
        } catch (Throwable error) {
            logger.accept(error);
        }
    }

    private void runCancelHook(Entry<?> entry) {
        if (entry != null && entry.cancelHook != null) runHook(entry.cancelHook);
    }

    private static final class Entry<T> {
        private final String key;
        private final Callable<T> callable;
        private final Consumer<T> success;
        private final Function<Throwable, T> fallback;
        private final Runnable cancelHook;
        private final AtomicBoolean done = new AtomicBoolean();
        private final AtomicBoolean cancelled = new AtomicBoolean();
        private volatile Future<?> future;
        private volatile Runnable timeout;
        private volatile Runnable delivery;

        private Entry(String key, Callable<T> callable, Consumer<T> success,
                Function<Throwable, T> fallback, Runnable cancelHook) {
            this.key = key;
            this.callable = callable;
            this.success = success;
            this.fallback = fallback;
            this.cancelHook = cancelHook;
        }
    }
}
