package com.fongmi.android.tv.player.vod;

import com.fongmi.android.tv.bean.Sub;
import com.fongmi.android.tv.player.PlaybackInput;
import com.fongmi.android.tv.event.ErrorEvent;
import com.fongmi.android.tv.player.session.Subscription;
import com.fongmi.android.tv.utils.TaskScheduler;

import java.util.ArrayDeque;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.Executor;

/** Serializes VOD inputs and owns the ordering between intent, session attachment and prepare. */
public final class VodPlaybackFlow implements PlaybackInput.Handler, AutoCloseable {
    public interface Port {
        boolean isCurrent();
        float speed();
        boolean selectSubtitle(Sub subtitle);
        /** Must attach a compatible delegate before returning; must not prepare it. */
        void attachSubtitleSession();
        void selectPlayer(int player);
        void toggleDecode(boolean persist);
        void nextPlayer();
        void seekPresentation(long position);
        void navigated(boolean next, boolean moved);
        default boolean reversePlayback() { return false; }
        default void clearForNewEpisode() {}
        void finish();
        default void saveBeforeStop() {}
        default boolean errorAlreadyReported(Throwable error) { return false; }
    }

    private final VodPlaybackCoordinator coordinator;
    private final Port port;
    private final ArrayDeque<Runnable> inputs = new ArrayDeque<>();
    private final Set<VodPlaybackObserver> observers = new LinkedHashSet<>();
    private final Executor delivery;
    private final Subscription subscription;
    private VodPlaybackState state;
    private VodRecoveryFlow recovery;
    private VodSourceSearch sources;
    private final VodSourceSwitchState sourceSwitch = new VodSourceSwitchState();
    private boolean dispatching;
    private boolean closed;
    private boolean loop;
    private long endedGeneration = -1;
    private long markerGeneration = -1;
    private final VodPlaybackIntent intent = new VodPlaybackIntent();
    private final VodHistoryCommitGate historyGate = new VodHistoryCommitGate();

    public VodPlaybackFlow(VodPlaybackCoordinator coordinator, Port port) {
        this(coordinator, port, Runnable::run);
    }

    public VodPlaybackFlow(VodPlaybackCoordinator coordinator, Port port, Executor delivery) {
        if (coordinator == null || port == null) throw new IllegalArgumentException("coordinator and port required");
        this.coordinator = coordinator;
        this.port = port;
        this.delivery = delivery;
        state = coordinator.state();
        subscription = coordinator.observe(value -> {
            long generation = coordinator.generation();
            enqueue(() -> {
                if (generation != coordinator.generation()) return;
                state = value;
                historyGate.onState(value);
                if (recovery != null) recovery.onPhase(value.getPhase());
                if (value.getPhase() == VodPlaybackPhase.READY) {
                    sourceSwitch.clear();
                    if (sources != null) sources.finish();
                }
                for (VodPlaybackObserver observer : new LinkedHashSet<>(observers)) {
                    if (closed || generation != coordinator.generation()) break;
                    try { observer.onVodPlaybackState(value); }
                    catch (RuntimeException ignored) { }
                }
                if (value.getPhase() == VodPlaybackPhase.ERROR && generation == coordinator.generation()
                        && !port.errorAlreadyReported(value.getError())) {
                    onError(value.getError() == null ? ErrorEvent.timeout() : ErrorEvent.extract(value.getError().getMessage()));
                }
                if (value.getPhase() == VodPlaybackPhase.ENDED && generation != endedGeneration) {
                    endedGeneration = generation;
                    enqueue(() -> {
                        if (generation != coordinator.generation() || coordinator.state().getPhase() != VodPlaybackPhase.ENDED) return;
                        if (loop) {
                            if (recovery != null) recovery.takeControl();
                            coordinator.replay();
                        }
                        else navigate(true);
                    });
                }
            });
        });
    }

    public void installRecovery(VodRecoveryFlow.Port port, TaskScheduler scheduler) {
        if (recovery != null) throw new IllegalStateException("recovery already installed");
        recovery = new VodRecoveryFlow(coordinator, port, scheduler);
        recovery.onPhase(state.getPhase());
    }

    public VodRecoveryFlow recovery() {
        if (recovery == null) throw new IllegalStateException("recovery not installed");
        return recovery;
    }

    public VodPlaybackState state() { return state; }
    public long requestGeneration() { return coordinator.generation(); }
    public boolean acceptsRequest(long generation) { return !closed && port.isCurrent() && generation == coordinator.generation(); }
    public VodPlaybackIntent intent() { return intent; }
    public boolean canCommitHistory() { return historyGate.canCommit(state); }
    public void resetHistoryGate() { historyGate.reset(); }
    public void setLoop(boolean loop) { this.loop = loop; }

    /** Capture before releasing a delegate; zero is an explicit, valid position. */
    public void preservePosition(long position) {
        VodPlaybackState current = coordinator.state();
        if (current.getEpisode() == null) return;
        coordinator.preserveControls(!coordinator.wantsPlayback(), port.speed());
        coordinator.preservePosition(current.getEpisode().getIdentity(), position);
    }

    public void onPosition(long position, long duration, long opening, long ending) {
        long generation = coordinator.generation();
        enqueue(() -> {
            if (generation != coordinator.generation() || !canCommitHistory()
                    || state.getPhase() != VodPlaybackPhase.READY || !coordinator.wantsPlayback()) return;
            if (opening > 0 && position > 0 && position < opening) {
                port.seekPresentation(opening);
                coordinator.seekTo(opening);
            } else if (ending > 0 && duration > 0 && position >= duration - ending && generation != markerGeneration) {
                markerGeneration = generation;
                navigate(true);
            }
        });
    }

    public void playEpisode(int index, StartPolicy policy) {
        enqueue(() -> {
            StartPolicy normalized = coordinator.normalizeStartPolicy(index, policy);
            if (normalized != StartPolicy.KEEP_CURRENT_POSITION) port.clearForNewEpisode();
            coordinator.play(index, normalized);
        });
    }

    public void reload() { enqueue(coordinator::reload); }
    /** Stops the current attempt without closing the page (detail changes or terminal errors). */
    public void stop() { enqueue(coordinator::stop); }
    public void reload(String format) { enqueue(() -> coordinator.reload(format)); }

    public void nextPlayer() {
        enqueue(() -> {
            preservePosition(coordinator.currentPosition());
            port.nextPlayer();
            coordinator.reload();
        });
    }

    public void resetFilm() {
        enqueue(() -> {
            if (recovery != null) recovery.reset();
            if (sources != null) sources.stop();
            sourceSwitch.clear();
            intent.clear();
            historyGate.reset();
            coordinator.reset();
        });
    }

    public void acceptSourceResult(com.fongmi.android.tv.bean.Result result) {
        enqueue(() -> { if (sources != null) sources.accept(result); });
    }

    public void installSources(VodSourceSearch.Port port) {
        if (sources != null) throw new IllegalStateException("sources already installed");
        sources = new VodSourceSearch(port, this::enqueue);
    }

    public VodSourceSearch sources() {
        if (sources == null) throw new IllegalStateException("sources not installed");
        return sources;
    }

    public VodSourceSwitchState sourceSwitch() { return sourceSwitch; }

    public Subscription observe(VodPlaybackObserver observer) {
        if (closed) throw new IllegalStateException("flow is closed");
        observers.add(observer);
        observer.onVodPlaybackState(state);
        return () -> observers.remove(observer);
    }

    public void onError(ErrorEvent error) {
        long generation = coordinator.generation();
        enqueue(() -> {
            if (generation == coordinator.generation() && recovery != null) {
                sourceSwitch.resumeSearch();
                recovery.recover(error);
            }
        });
    }

    public void setPlaybackAllowed(boolean allowed) { enqueue(() -> coordinator.setPlaybackAllowed(allowed)); }

    @Override public void dispatch(PlaybackInput input) {
        if (closed || !port.isCurrent() || input == null) return;
        enqueue(() -> handle(input));
    }

    private void enqueue(Runnable input) {
        delivery.execute(() -> accept(input));
    }

    private void accept(Runnable input) {
        if (closed) return;
        inputs.add(input);
        if (dispatching) return;
        dispatching = true;
        try {
            while (!closed && !inputs.isEmpty()) inputs.remove().run();
        } finally {
            dispatching = false;
        }
    }

    private void handle(PlaybackInput input) {
        if (!port.isCurrent()) return;
        switch (input.getType()) {
            case PLAY: coordinator.play(); break;
            case PAUSE: coordinator.pause(); break;
            case SEEK:
                port.seekPresentation(input.getPosition());
                coordinator.seekTo(input.getPosition());
                break;
            case NEXT: navigate(true); break;
            case PREVIOUS: navigate(false); break;
            case STOP: port.saveBeforeStop(); coordinator.stop(); port.finish(); break;
            case SUBTITLE: subtitle(input.getSubtitle()); break;
            case PLAYER:
                if (recovery != null) recovery.takeControl();
                preservePosition(coordinator.currentPosition());
                port.selectPlayer(input.getPlayer());
                coordinator.reload();
                break;
            case DECODE:
                if (input.isPersistent() && recovery != null) recovery.takeControl();
                preservePosition(coordinator.currentPosition());
                port.toggleDecode(input.isPersistent());
                coordinator.reload();
                break;
        }
    }

    private void navigate(boolean next) {
        if (recovery != null) recovery.takeControl();
        boolean forward = next != port.reversePlayback();
        boolean moved = forward ? coordinator.next() : coordinator.previous();
        port.navigated(next, moved);
    }

    private void subtitle(Sub subtitle) {
        if (!port.selectSubtitle(subtitle)) return;
        VodPlaybackState before = coordinator.state();
        if (before.getEpisode() != null) {
            coordinator.preserveControls(!coordinator.wantsPlayback(), port.speed());
            coordinator.preservePosition(before.getEpisode().getIdentity(), coordinator.currentPosition());
        }
        port.attachSubtitleSession();
        coordinator.reload();
    }

    @Override public void close() {
        if (closed) return;
        closed = true;
        subscription.cancel();
        if (recovery != null) recovery.close();
        if (sources != null) sources.close();
        sourceSwitch.clear();
        inputs.clear();
        observers.clear();
        coordinator.close();
        state = coordinator.state();
    }
}
