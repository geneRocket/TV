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
        if (delegateSubscription != null) delegateSubscription.cancel();
        delegate = next;
        delegateSubscription = next.observe(value -> {
            // A freshly created engine always emits an empty IDLE snapshot. The
            // wrapper has stable identity, so that bootstrap event must not make
            // controls flicker back to idle while replacing player/decode mode.
            if (value.getPhase() == PlaybackPhase.IDLE && value.getItem() == null) return;
            publish(value);
        });
    }

    @Override public long prepare(PlaybackItem item) { return delegate.prepare(item); }
    @Override public void play() { delegate.play(); }
    @Override public void pause() { delegate.pause(); }
    @Override public void seekTo(long position) { delegate.seekTo(position); }
    @Override public void setSpeed(float speed) { delegate.setSpeed(speed); }
    @Override public void setTrack(Track track) { delegate.setTrack(track); }
    @Override public EngineSnapshot snapshot() { return delegate.snapshot(); }
    @Override public void stop() { delegate.stop(); }
    @Override public synchronized PlaybackState state() { return state; }

    @Override public synchronized Subscription observe(PlaybackObserver observer) {
        observers.add(observer);
        observer.onPlaybackState(state);
        return () -> remove(observer);
    }

    private synchronized void remove(PlaybackObserver observer) { observers.remove(observer); }
    private synchronized void publish(PlaybackState next) {
        state = next;
        for (PlaybackObserver observer : new LinkedHashSet<>(observers)) observer.onPlaybackState(next);
    }

    @Override public synchronized void release() {
        if (released) return;
        released = true;
        if (delegateSubscription != null) delegateSubscription.cancel();
        delegate.release();
        observers.clear();
    }
}
