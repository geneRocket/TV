package com.fongmi.android.tv.player.vod;

import com.fongmi.android.tv.bean.History;
import java.util.function.Supplier;

/** Shared progress sampling and persistence boundary; the page supplies only its current history object. */
public final class VodHistorySession {
    private final VodPlaybackCoordinator coordinator;
    private final VodPlaybackFlow flow;
    private final VodHistoryController controller;
    private final Supplier<History> history;
    public VodHistorySession(VodPlaybackCoordinator coordinator, VodPlaybackFlow flow,
                             VodHistoryController controller, Supplier<History> history) {
        this.coordinator = coordinator;
        this.flow = flow;
        this.controller = controller;
        this.history = history;
        flow.setHandoff(this::save);
    }
    public boolean canCommit() { return history.get() != null && flow.canCommitHistory(); }
    public void save() {
        VodPlaybackCheckpoint snapshot = coordinator.checkpoint();
        controller.saveNow(history.get(), canCommit(), snapshot.getPosition(), snapshot.getDuration());
    }
    public void tick(long now) {
        History current = history.get();
        VodPlaybackCheckpoint snapshot = coordinator.checkpoint();
        controller.saveTick(current, canCommit(), snapshot.getPosition(), snapshot.getDuration(), now);
        if (current != null) flow.onPosition(snapshot.getPosition(), snapshot.getDuration(), current.getOpening(), current.getEnding());
    }
}
