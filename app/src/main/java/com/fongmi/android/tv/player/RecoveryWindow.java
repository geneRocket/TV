package com.fongmi.android.tv.player;

import com.fongmi.android.tv.utils.TaskScheduler;

/** One deadline across retries, parser changes and source searches. Main-thread owned. */
public final class RecoveryWindow {
    public enum Phase { IDLE, RECOVERING, STABILIZING, RECOVERED, CANCELLED }
    public static final long DEFAULT_LIMIT_MS = 45_000;
    private final TaskScheduler scheduler;
    private final Runnable exhausted;
    private final Runnable timeout = this::expire;
    private Phase phase = Phase.IDLE;
    private final Runnable stable = () -> {
        if (phase == Phase.STABILIZING) reset();
    };

    public RecoveryWindow(TaskScheduler scheduler, Runnable exhausted) {
        this.scheduler = scheduler;
        this.exhausted = exhausted;
    }

    public boolean begin() {
        scheduler.remove(stable);
        if (phase == Phase.CANCELLED) return false;
        boolean wasActive = isActive();
        phase = Phase.RECOVERING;
        if (!wasActive) {
            scheduler.post(timeout, DEFAULT_LIMIT_MS);
        }
        return true;
    }

    public void ready() {
        if (!isActive() || phase == Phase.STABILIZING) return;
        phase = Phase.STABILIZING;
        scheduler.post(stable, 5_000);
    }

    public void unstable() {
        scheduler.remove(stable);
        if (phase == Phase.STABILIZING) phase = Phase.RECOVERED;
    }
    public Phase getPhase() { return phase; }
    public boolean isActive() {
        return phase == Phase.RECOVERING || phase == Phase.STABILIZING || phase == Phase.RECOVERED;
    }
    /** A retained retry budget must not intercept Back once playback has succeeded. */
    public boolean isRecovering() { return phase == Phase.RECOVERING; }

    public void cancel() {
        scheduler.remove(timeout);
        scheduler.remove(stable);
        phase = Phase.CANCELLED;
    }

    public void reset() { cancel(); phase = Phase.IDLE; }

    private void expire() {
        if (!isActive()) return;
        if (!isRecovering()) {
            reset();
            return;
        }
        cancel();
        exhausted.run();
    }
}
