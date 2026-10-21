package com.fongmi.android.tv.utils;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

/** Page/film-scoped UI callbacks. Removing a task retains Handler's identity semantics. */
public final class CallbackScope implements AutoCloseable {
    private final TaskScheduler scheduler;
    private final Map<Runnable, Set<Pending>> tasks = new IdentityHashMap<>();
    private long generation;
    private boolean closed;

    public CallbackScope(TaskScheduler scheduler) { this.scheduler = scheduler; }
    public synchronized long capture() { return generation; }
    public synchronized boolean accepts(long token) { return !closed && token == generation; }
    public void post(Runnable task) { post(task, 0); }

    public synchronized void post(Runnable task, long delay) {
        if (closed || task == null) return;
        Pending pending = new Pending(task, generation);
        tasks.computeIfAbsent(task, key -> Collections.newSetFromMap(new IdentityHashMap<>())).add(pending);
        scheduler.post(pending, delay);
    }

    public synchronized void remove(Runnable... originals) {
        for (Runnable original : originals) {
            Set<Pending> pending = tasks.remove(original);
            if (pending != null) for (Pending task : pending) scheduler.remove(task);
        }
    }

    public synchronized void reset() {
        generation++;
        for (Set<Pending> pending : tasks.values()) for (Pending task : pending) scheduler.remove(task);
        tasks.clear();
    }

    @Override public synchronized void close() { closed = true; reset(); }

    private final class Pending implements Runnable {
        private final Runnable original;
        private final long token;
        Pending(Runnable original, long token) { this.original = original; this.token = token; }
        @Override public void run() {
            synchronized (CallbackScope.this) {
                Set<Pending> pending = tasks.get(original);
                if (pending == null || !pending.remove(this)) return;
                if (pending.isEmpty()) tasks.remove(original);
                if (closed || token != generation) return;
            }
            original.run();
        }
    }
}
