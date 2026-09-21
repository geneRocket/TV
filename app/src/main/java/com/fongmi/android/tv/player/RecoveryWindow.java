package com.fongmi.android.tv.player;

import com.fongmi.android.tv.utils.TaskScheduler;

/** One deadline across retries, parser changes and source searches. Main-thread owned. */
public final class RecoveryWindow {
    public static final long DEFAULT_LIMIT_MS = 45_000;
    private final TaskScheduler scheduler;
    private final Runnable exhausted;
    private final Runnable timeout = this::expire;
    private final Runnable stable = this::reset;
    private boolean active;
    private boolean cancelled;

    public RecoveryWindow(TaskScheduler scheduler, Runnable exhausted) {
        this.scheduler = scheduler;
        this.exhausted = exhausted;
    }

    public boolean begin() {
        scheduler.remove(stable);
        if (cancelled) return false;
        if (!active) {
            active = true;
            scheduler.post(timeout, DEFAULT_LIMIT_MS);
        }
        return true;
    }

    public void ready() {
        scheduler.remove(stable);
        if (active) scheduler.post(stable, 5_000);
    }

    public void unstable() { scheduler.remove(stable); }
    public boolean isActive() { return active; }

    public void cancel() {
        scheduler.remove(timeout);
        scheduler.remove(stable);
        active = false;
        cancelled = true;
    }

    public void reset() { cancel(); cancelled = false; }

    private void expire() {
        if (!active) return;
        cancel();
        exhausted.run();
    }
}
