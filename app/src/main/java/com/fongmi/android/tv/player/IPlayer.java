package com.fongmi.android.tv.player;

import com.fongmi.android.tv.bean.Track;

public interface IPlayer {

    void setMediaSource(PlayerMediaSource source);

    void play();

    void pause();

    void stop();

    void release();

    void seekTo(long position);

    long getPosition();

    long getDuration();

    long getBuffered();

    float getSpeed();

    void setSpeed(float speed);

    int getVideoWidth();

    int getVideoHeight();

    boolean isPlaying();

    void setTrack(Track track);
}
