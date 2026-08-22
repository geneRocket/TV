package com.fongmi.android.tv.player.session;

public interface PlayerSession {

    long prepare(PlaybackItem item);

    void play();

    void pause();

    void seekTo(long position);

    void setSpeed(float speed);

    void setTrack(com.fongmi.android.tv.bean.Track track);

    com.fongmi.android.tv.player.engine.EngineSnapshot snapshot();

    void stop();

    PlaybackState state();

    Subscription observe(PlaybackObserver observer);

    void release();
}
