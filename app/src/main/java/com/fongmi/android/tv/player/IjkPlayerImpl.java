package com.fongmi.android.tv.player;

import com.fongmi.android.tv.bean.Drm;
import com.fongmi.android.tv.bean.Sub;
import com.fongmi.android.tv.bean.Track;
import com.fongmi.android.tv.Setting;

import java.util.List;
import java.util.Map;

import tv.danmaku.ijk.media.player.IMediaPlayer;
import tv.danmaku.ijk.media.player.ui.IjkVideoView;

public class IjkPlayerImpl implements IPlayer {

    private final IjkVideoView player;

    public IjkPlayerImpl(IjkVideoView view, int playerType, int decode, IMediaPlayer.Listener listener) {
        player = view.render(Setting.getRender()).decode(decode);
        player.addListener(listener);
        player.setPlayer(playerType);
    }

    @Override
    public void setMediaSource(Map<String, String> headers, String url, String format, Drm drm, List<Sub> subs, long position, boolean forceLive) {
        player.setMediaSource(IjkUtil.getSource(headers, url), position);
    }

    @Override
    public void play() {
        player.start();
    }

    @Override
    public void pause() {
        player.pause();
    }

    @Override
    public void stop() {
        player.stop();
    }

    @Override
    public void release() {
        player.release();
    }

    @Override
    public void seekTo(long position) {
        player.seekTo(position);
    }

    @Override
    public long getPosition() {
        return player.getCurrentPosition();
    }

    @Override
    public long getDuration() {
        return player.getDuration();
    }

    @Override
    public long getBuffered() {
        return player.getBufferedPosition();
    }

    @Override
    public float getSpeed() {
        return player.getSpeed();
    }

    @Override
    public void setSpeed(float speed) {
        player.setSpeed(speed);
    }

    @Override
    public int getVideoWidth() {
        return player.getVideoWidth();
    }

    @Override
    public int getVideoHeight() {
        return player.getVideoHeight();
    }

    @Override
    public boolean isPlaying() {
        return player.isPlaying();
    }

    @Override
    public void setTrack(Track item) {
        if (item.isSelected()) {
            player.selectTrack(item.getType(), item.getTrack());
        } else {
            player.deselectTrack(item.getType(), item.getTrack());
        }
    }
}
