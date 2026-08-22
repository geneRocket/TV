package com.fongmi.android.tv.player.session;

import com.fongmi.android.tv.bean.Track;
import com.fongmi.android.tv.player.engine.EngineSnapshot;

import java.util.LinkedHashSet;
import java.util.Set;

/** Stable session identity whose engine-backed delegate can be replaced for player/decode changes. */
public final class SwitchablePlayerSession implements PlayerSession {

    private final Set<PlaybackObserver> observers = new LinkedHashSet<>();
    private PlayerSession delegate;
    private Subscription delegateSubscription;
    private PlaybackState state = PlaybackState.IDLE;
    private boolean released;

    public SwitchablePlayerSession(PlayerSession delegate) { replace(delegate); }

    public synchronized void replace(PlayerSession next) {
        if (released) throw new IllegalStateException("session is released");
        if (next == null) throw new IllegalArgumentException("delegate must not be null");
        if (next == delegate) return;
        PlayerSession previous = delegate;
        Subscription previousSubscription = delegateSubscription;
        if (previousSubscription != null) previousSubscription.cancel();
        delegate = next;
        delegateSubscription = next.observe(value -> {
            // A freshly created engine always emits an empty IDLE snapshot. The
            // wrapper has stable identity, so that bootstrap event must not make
            // controls flicker back to idle while replacing player/decode mode.
            if (value.getPhase() == PlaybackPhase.IDLE && value.getItem() == null) return;
            publish(value);
        });
        if (previous != null) previous.release();
    }

    @Override public synchronized long prepare(PlaybackItem item) { requireActive(); return delegate.prepare(item); }
    @Override public synchronized void play() { requireActive(); delegate.play(); }
    @Override public synchronized void pause() { requireActive(); delegate.pause(); }
    @Override public synchronized void seekTo(long position) { requireActive(); delegate.seekTo(position); }
    @Override public synchronized void setSpeed(float speed) { requireActive(); delegate.setSpeed(speed); }
    @Override public synchronized void setTrack(Track track) { requireActive(); delegate.setTrack(track); }
    @Override public synchronized EngineSnapshot snapshot() { requireActive(); return delegate.snapshot(); }
    @Override public synchronized void stop() { if (!released) delegate.stop(); }
    @Override public synchronized PlaybackState state() { return state; }

    @Override public synchronized Subscription observe(PlaybackObserver observer) {
        requireActive();
        if (observer == null) throw new IllegalArgumentException("observer must not be null");
        observers.add(observer);
        try {
            observer.onPlaybackState(state);
        } catch (RuntimeException ignored) {
        }
        return () -> remove(observer);
    }

    private synchronized void remove(PlaybackObserver observer) { observers.remove(observer); }
    private synchronized void publish(PlaybackState next) {
        if (released || next == null) return;
        state = next;
        for (PlaybackObserver observer : new LinkedHashSet<>(observers)) {
            try {
                observer.onPlaybackState(next);
            } catch (RuntimeException ignored) {
                // One presentation observer must not interrupt state propagation.
            }
        }
    }

    @Override public synchronized void release() {
        if (released) return;
        released = true;
        if (delegateSubscription != null) delegateSubscription.cancel();
        delegate.release();
        state = new PlaybackState(state.getSessionId(), PlaybackPhase.RELEASED, state.getItem(), state.getPosition(), state.getDuration(), null);
        for (PlaybackObserver observer : new LinkedHashSet<>(observers)) {
            try {
                observer.onPlaybackState(state);
            } catch (RuntimeException ignored) {
            }
        }
        observers.clear();
    }

    private void requireActive() {
        if (released) throw new IllegalStateException("session is released");
    }
}
