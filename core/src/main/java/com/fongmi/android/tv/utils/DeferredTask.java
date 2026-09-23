package com.fongmi.android.tv.utils;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Scheduler-thread owned work that runs once after readiness settles, without polling. */
public final class DeferredTask<T> implements AutoCloseable {
    public interface Work<T> extends Callable<T> {
        void cancel();
    }

    private final TaskScope tasks;
    private final TaskScheduler scheduler;
    private final Runnable start = this::start;
    private Supplier<Work<T>> factory;
    private Consumer<T> callback;
    private boolean ready;
    private boolean submitted;
    private boolean completed;
    private boolean closed;

    public DeferredTask(ExecutorService executor, TaskScheduler scheduler, Consumer<Throwable> logger) {
        this.scheduler = scheduler;
        tasks = new TaskScope(executor, scheduler, logger, false);
    }

    public void load(Supplier<Work<T>> factory, Consumer<T> callback) {
        if (closed) return;
        suspend();
        this.factory = factory;
        this.callback = callback;
        completed = false;
        schedule();
    }

    public void setReady(boolean ready) {
        if (closed || this.ready == ready) return;
        this.ready = ready;
        if (ready) schedule();
        else suspend();
    }

    private void schedule() {
        if (ready && factory != null && !completed && !submitted) scheduler.post(start, 1500);
    }

    private void suspend() {
        scheduler.remove(start);
        tasks.cancel("deferred");
        submitted = false;
    }

    private void start() {
        if (closed || !ready || completed || submitted || factory == null) return;
        Work<T> work = factory.get();
        submitted = true;
        tasks.submit("deferred", work, 0, value -> {
            submitted = false;
            completed = true;
            if (value != null) callback.accept(value);
        }, error -> null, work::cancel);
    }

    @Override public void close() {
        if (closed) return;
        closed = true;
        scheduler.remove(start);
        tasks.close();
        factory = null;
        callback = null;
    }
}
