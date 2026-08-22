package com.fongmi.android.tv.player.session;

import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.bean.Track;
import com.fongmi.android.tv.player.PlaybackController;
import com.fongmi.android.tv.player.engine.EngineSnapshot;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Compatibility session for screens migrating to the VOD coordinator.  The
 * coordinator owns episode/resolve/progress state; PlaybackController remains
 * only the media and accessory implementation during the UI migration.
 */
public final class ControllerPlayerSession implements PlayerSession {

    private final Set<PlaybackObserver> observers = new LinkedHashSet<>();
    private final PlaybackController controller;
    private final int timeout;
    private final Subscription subscription;
    private PlaybackItem item;
    private PlaybackState state = PlaybackState.IDLE;
    private long sessionId;
    private boolean released;

    public ControllerPlayerSession(PlaybackController controller, int timeout) {
        this.controller = controller;
        this.timeout = timeout;
        this.subscription = controller.observe(this::onControllerState);
    }

    @Override public synchronized long prepare(PlaybackItem item) {
        requireActive();
        this.item = item;
        long id = ++sessionId;
        publish(new PlaybackState(id, PlaybackPhase.PREPARING, item, item.getStartPosition(), -1, null));
        controller.setNextStartPosition(item.getStartPosition());
        Result result = item.getSourceResult();
        if (result == null) {
            result = Result.empty();
            result.setUrl(item.getUrl());
            result.setFormat(item.getFormat());
            result.setDrm(item.getDrm());
        }
        controller.start(result, true, timeout);
        return id;
    }

    private synchronized void onControllerState(PlaybackState source) {
        if (released || item == null) return;
        publish(new PlaybackState(sessionId, source.getPhase(), item, source.getPosition(), source.getDuration(), source.getError()));
    }

    @Override public void play() { controller.play(); }
    @Override public void pause() { controller.pause(); }
    @Override public void seekTo(long position) { controller.seekTo(position); }
    @Override public void setSpeed(float speed) { controller.setSpeed(speed); }
    @Override public void setTrack(Track track) { controller.setTrack(java.util.Collections.singletonList(track)); }
    @Override public EngineSnapshot snapshot() { return new EngineSnapshot(sessionId, com.fongmi.android.tv.player.engine.EnginePhase.valueOf(state.getPhase().name()), controller.getPosition(), controller.getDuration(), controller.getBuffered(), controller.isPlaying(), controller.getSpeed(), controller.getVideoWidth(), controller.getVideoHeight()); }
    @Override public void stop() { controller.stop(); }
    @Override public synchronized PlaybackState state() { return state; }

    @Override public synchronized Subscription observe(PlaybackObserver observer) {
        requireActive();
        observers.add(observer);
        observer.onPlaybackState(state);
        return () -> remove(observer);
    }

    private synchronized void remove(PlaybackObserver observer) { observers.remove(observer); }

    private void publish(PlaybackState next) {
        state = next;
        for (PlaybackObserver observer : new LinkedHashSet<>(observers)) observer.onPlaybackState(next);
    }

    @Override public synchronized void release() {
        if (released) return;
        released = true;
        subscription.cancel();
        controller.release();
        observers.clear();
    }

    private void requireActive() {
        if (released) throw new IllegalStateException("session is released");
    }
}
