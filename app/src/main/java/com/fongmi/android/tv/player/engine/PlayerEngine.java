package com.fongmi.android.tv.player.engine;

import com.fongmi.android.tv.bean.Track;

public interface PlayerEngine {

    /**
     * Prepares a media source for the supplied request. Implementations must apply
     * {@link EngineRequest#getStartPosition()} before publishing READY or PAUSED.
     */
    void prepare(EngineRequest request, Listener listener);

    void play();

    void pause();

    void seekTo(long position);

    void stop();

    void setSpeed(float speed);

    void setTrack(Track track);

    EngineSnapshot snapshot();

    void release();

    interface Listener {
        void onEngineEvent(EngineEvent event);
    }
}
