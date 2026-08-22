package com.fongmi.android.tv.player.engine;

import com.fongmi.android.tv.bean.Track;

public interface PlayerEngine {

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
