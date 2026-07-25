package com.fongmi.android.tv.player;

import com.fongmi.android.tv.bean.Drm;
import com.fongmi.android.tv.bean.Sub;
import com.fongmi.android.tv.bean.Track;

import java.util.List;
import java.util.Map;

public interface IPlayer {

    void setMediaSource(Map<String, String> headers, String url, String format, Drm drm, List<Sub> subs, long position, boolean forceLive);

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
