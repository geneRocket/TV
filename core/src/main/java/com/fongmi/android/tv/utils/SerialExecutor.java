package com.fongmi.android.tv.utils;

import java.util.ArrayDeque;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/** One FIFO drain on any backing executor, including direct and concurrent executors. */
public final class SerialExecutor implements Executor, AutoCloseable {
    private final Executor executor;
    private final Consumer<RuntimeException> errors;
    private final ArrayDeque<Runnable> queue = new ArrayDeque<>();
    private boolean scheduled;
    private boolean closed;

    public SerialExecutor(Executor executor, Consumer<RuntimeException> errors) {
        this.executor = Objects.requireNonNull(executor);
        this.errors = Objects.requireNonNull(errors);
    }

    @Override public void execute(Runnable task) {
        Objects.requireNonNull(task);
        synchronized (queue) {
            if (closed) return;
            queue.add(task);
            if (scheduled) return;
            scheduled = true;
        }
        try { executor.execute(this::drain); }
        catch (RuntimeException failure) {
            synchronized (queue) { scheduled = false; queue.clear(); }
            throw failure;
        }
    }

    private void drain() {
        while (true) {
            Runnable next;
            synchronized (queue) {
                next = queue.poll();
                if (next == null) { scheduled = false; return; }
            }
            try { next.run(); }
            catch (RuntimeException failure) {
                // Reporting must not strand commands already accepted by the queue.
                try { errors.accept(failure); }
                catch (RuntimeException ignored) { }
            }
        }
    }

    /** Drops queued work. A command already running is allowed to finish. */
    public void clear() {
        synchronized (queue) { queue.clear(); }
    }

    @Override public void close() {
        synchronized (queue) { closed = true; queue.clear(); }
    }
}
