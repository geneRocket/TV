package com.fongmi.android.tv.player.session;

import com.fongmi.android.tv.player.engine.EngineEvent;
import com.fongmi.android.tv.player.engine.EnginePhase;
import com.fongmi.android.tv.player.engine.EngineRequest;
import com.fongmi.android.tv.player.engine.PlayerEngine;

import java.util.LinkedHashSet;
import java.util.Set;

public final class DefaultPlayerSession implements PlayerSession, PlayerEngine.Listener {

    private final Set<PlaybackObserver> observers = new LinkedHashSet<>();
    private final PlayerEngine engine;
    private PlaybackState state = PlaybackState.IDLE;
    private long nextSessionId;
    private boolean released;

    public DefaultPlayerSession(PlayerEngine engine) {
        if (engine == null) throw new IllegalArgumentException("engine must not be null");
        this.engine = engine;
    }

    @Override
    public synchronized long prepare(PlaybackItem item) {
        requireActive();
        if (item == null) throw new IllegalArgumentException("item must not be null");
        long sessionId = ++nextSessionId;
        publish(new PlaybackState(sessionId, PlaybackPhase.PREPARING, item, item.getStartPosition(), -1, null));
        engine.prepare(new EngineRequest(sessionId, item.getUrl(), item.getHeaders(), item.getFormat(), item.getDrm(), item.getSubtitles(), item.getStartPosition(), item.isForceLive()), this);
        return sessionId;
    }

    @Override
    public synchronized void play() {
        requireActive();
        engine.play();
    }

    @Override
    public synchronized void pause() {
        requireActive();
        engine.pause();
    }

    @Override
    public synchronized void seekTo(long position) {
        requireActive();
        engine.seekTo(Math.max(position, 0));
    }

    @Override
    public synchronized void setSpeed(float speed) {
        requireActive();
        engine.setSpeed(speed);
    }

    @Override
    public synchronized void setTrack(com.fongmi.android.tv.bean.Track track) {
        requireActive();
        engine.setTrack(track);
    }

    @Override
    public synchronized com.fongmi.android.tv.player.engine.EngineSnapshot snapshot() {
        return engine.snapshot();
    }

    @Override
    public synchronized void stop() {
        if (released) return;
        engine.stop();
        publish(new PlaybackState(state.getSessionId(), PlaybackPhase.STOPPED, state.getItem(), state.getPosition(), state.getDuration(), null));
    }

    @Override
    public synchronized PlaybackState state() {
        return state;
    }

    @Override
    public synchronized Subscription observe(PlaybackObserver observer) {
        requireActive();
        if (observer == null) throw new IllegalArgumentException("observer must not be null");
        observers.add(observer);
        observer.onPlaybackState(state);
        return () -> removeObserver(observer);
    }

    private synchronized void removeObserver(PlaybackObserver observer) {
        observers.remove(observer);
    }

    @Override
    public synchronized void release() {
        if (released) return;
        released = true;
        engine.release();
        state = new PlaybackState(state.getSessionId(), PlaybackPhase.RELEASED, state.getItem(), state.getPosition(), state.getDuration(), null);
        for (PlaybackObserver observer : snapshotObservers()) observer.onPlaybackState(state);
        observers.clear();
    }

    @Override
    public synchronized void onEngineEvent(EngineEvent event) {
        if (released || event == null || event.getSessionId() != state.getSessionId()) return;
        publish(new PlaybackState(event.getSessionId(), map(event.getPhase()), state.getItem(), event.getPosition(), event.getDuration(), event.getError()));
    }

    private PlaybackPhase map(EnginePhase phase) {
        return PlaybackPhase.valueOf(phase.name());
    }

    private void publish(PlaybackState next) {
        state = next;
        for (PlaybackObserver observer : snapshotObservers()) {
            try {
                observer.onPlaybackState(next);
            } catch (RuntimeException ignored) {
                // State publication is authoritative; one UI observer cannot break the engine callback.
            }
        }
    }

    private Set<PlaybackObserver> snapshotObservers() {
        return new LinkedHashSet<>(observers);
    }

    private void requireActive() {
        if (released) throw new IllegalStateException("session is released");
    }
}
