package com.fongmi.android.tv.player;

import com.fongmi.android.tv.event.ErrorEvent;
import com.fongmi.android.tv.player.session.PlaybackPhase;
import com.fongmi.android.tv.player.session.PlaybackState;
import com.fongmi.android.tv.player.session.PlaybackObserver;
import com.fongmi.android.tv.player.session.Subscription;

import java.util.ArrayDeque;
import java.util.concurrent.Executor;

/** Shared transport, lifecycle and recovery ownership for live TV and the DLNA renderer. */
public final class DirectPlaybackFlow implements PlaybackInput.Handler, AutoCloseable {
    public interface Engine {
        boolean isCurrent();
        int getPlayer();
        void executeInput(PlaybackInput input);
        Subscription observe(PlaybackObserver observer);
    }

    public interface Port {
        void render(PlaybackState state);
        void navigate(boolean next);
        void finish();
        void selectPlayer(int player);
        void decode(boolean persist);
        boolean hasPlayableItem();
        void retry();
        void reloadExo(ErrorEvent error);
        void switchPlayer();
        void advance(ErrorEvent error);
        void error(ErrorEvent error);
        void ended();
        default void userPlayback(boolean playing) {}
    }

    private final Engine engine;
    private final Port port;
    private final Executor delivery;
    private final Subscription subscription;
    private final PlaybackRecovery recovery = new PlaybackRecovery();
    private final ArrayDeque<Runnable> queue = new ArrayDeque<>();
    private boolean draining;
    private boolean closed;
    private boolean wantsPlayback = true;
    private boolean allowed = true;
    private boolean recovering;
    private long generation;
    private long endedSession = -1;
    private PlaybackState state = PlaybackState.IDLE;

    public DirectPlaybackFlow(Engine engine, Port port, Executor delivery) {
        this.engine = engine;
        this.port = port;
        this.delivery = delivery;
        subscription = engine.observe(value -> {
            long expected = generation;
            enqueue(() -> {
                if (expected != generation) return;
                state = value;
                if (value.getPhase() == PlaybackPhase.READY) recovery.reset();
                port.render(value);
                if (value.getPhase() == PlaybackPhase.ENDED && value.getSessionId() != endedSession) {
                    endedSession = value.getSessionId();
                    enqueue(() -> {
                        if (expected != generation || state != value) return;
                        wantsPlayback = false;
                        port.ended();
                    });
                }
            });
        });
    }

    public PlaybackState state() { return state; }
    public long generation() { return generation; }
    public boolean accepts(long token) { return !closed && engine.isCurrent() && token == generation; }
    public boolean isBuffering() { return state.getPhase() == PlaybackPhase.PREPARING || state.getPhase() == PlaybackPhase.BUFFERING; }
    @Override public boolean playWhenReady(boolean requested) { return requested && wantsPlayback && allowed && !closed; }

    /** A user-selected channel/program/cast URI is a new source; retries keep the current budget. */
    public void beginSource() {
        generation++;
        if (!recovering) recovery.reset();
        endedSession = -1;
        wantsPlayback = true;
    }

    public void setPlaybackAllowed(boolean value) {
        enqueue(() -> {
            allowed = value;
            engine.executeInput(PlaybackInput.action(value && wantsPlayback ? PlaybackInput.Type.PLAY : PlaybackInput.Type.PAUSE));
        });
    }

    @Override public void dispatch(PlaybackInput input) {
        if (input == null) return;
        enqueue(() -> {
            switch (input.getType()) {
                case PLAY:
                case PAUSE:
                    wantsPlayback = input.getType() == PlaybackInput.Type.PLAY;
                    engine.executeInput(PlaybackInput.action(wantsPlayback && allowed ? PlaybackInput.Type.PLAY : PlaybackInput.Type.PAUSE));
                    port.userPlayback(wantsPlayback);
                    break;
                case SEEK:
                case SUBTITLE: engine.executeInput(input); break;
                case NEXT: port.navigate(true); break;
                case PREVIOUS: port.navigate(false); break;
                case STOP:
                    wantsPlayback = false;
                    engine.executeInput(input);
                    port.finish();
                    break;
                case PLAYER:
                    recovery.reset();
                    port.selectPlayer(input.getPlayer());
                    break;
                case DECODE:
                    if (input.isPersistent()) recovery.reset();
                    port.decode(input.isPersistent());
                    break;
            }
        });
    }

    public void onError(ErrorEvent error) {
        long expected = generation;
        enqueue(() -> {
            if (expected != generation || error == null) return;
            recovering = true;
            try {
                switch (recovery.next(error, engine.getPlayer() == PlayerType.EXO, engine.getPlayer(), port.hasPlayableItem(), true)) {
                    case RETRY: port.retry(); break;
                    case TOGGLE_DECODE: port.decode(false); break;
                    case RELOAD_EXO: port.reloadExo(error); break;
                    case SWITCH_PLAYER: port.switchPlayer(); break;
                    case ADVANCE: port.advance(error); break;
                    case STOP: port.error(error); break;
                }
            } finally { recovering = false; }
        });
    }

    private void enqueue(Runnable task) {
        delivery.execute(() -> {
            if (closed || !engine.isCurrent()) return;
            queue.add(task);
            if (draining) return;
            draining = true;
            try {
                while (!closed && !queue.isEmpty()) {
                    Runnable next = queue.remove();
                    if (engine.isCurrent()) next.run();
                }
            } finally { draining = false; }
        });
    }

    @Override public void close() {
        closed = true;
        generation++;
        subscription.cancel();
        queue.clear();
    }
}
