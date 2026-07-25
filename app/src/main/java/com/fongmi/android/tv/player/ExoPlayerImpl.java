package com.fongmi.android.tv.player;

import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.Setting;
import com.fongmi.android.tv.bean.Drm;
import com.fongmi.android.tv.bean.Sub;
import com.fongmi.android.tv.bean.Track;
import com.fongmi.android.tv.player.exo.ExoUtil;
import com.fongmi.android.tv.utils.UrlUtil;

import java.util.List;
import java.util.Map;

public class ExoPlayerImpl implements IPlayer {

    private final ExoPlayer player;
    private final Player.Listener listener;
    private final int decode;

    public ExoPlayerImpl(PlayerView view, int decode, Player.Listener listener) {
        this.listener = listener;
        this.decode = decode;
        player = new ExoPlayer.Builder(App.get())
                .setLoadControl(ExoUtil.buildLoadControl())
                .setTrackSelector(ExoUtil.buildTrackSelector())
                .setRenderersFactory(ExoUtil.buildRenderersFactory(decode))
                .setMediaSourceFactory(ExoUtil.buildMediaSourceFactory())
                .setVideoChangeFrameRateStrategy(C.VIDEO_CHANGE_FRAME_RATE_STRATEGY_ONLY_IF_SEAMLESS)
                .build();
        player.setAudioAttributes(AudioAttributes.DEFAULT, !Setting.isPlayWithOthers());
        player.setHandleAudioBecomingNoisy(true);
        player.setPlayWhenReady(true);
        player.addListener(listener);
        view.setPlayer(player);
    }

    public ExoPlayer getPlayer() {
        return player;
    }

    @Override
    public void setMediaSource(Map<String, String> headers, String url, String format, Drm drm, List<Sub> subs, long position, boolean forceLive) {
        MediaItem item = ExoUtil.getMediaItem(headers, UrlUtil.uri(url), format, drm, subs, decode, forceLive);
        player.setMediaItem(item, position);
        player.prepare();
    }

    @Override
    public void play() {
        player.play();
    }

    @Override
    public void pause() {
        player.pause();
    }

    @Override
    public void stop() {
        player.stop();
        player.clearMediaItems();
    }

    @Override
    public void release() {
        player.stop();
        player.clearVideoSurface();
        player.removeListener(listener);
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
        return player.getPlaybackParameters().speed;
    }

    @Override
    public void setSpeed(float speed) {
        if (!Setting.isTunnel()) player.setPlaybackSpeed(speed);
    }

    @Override
    public int getVideoWidth() {
        return player.getVideoSize().width;
    }

    @Override
    public int getVideoHeight() {
        return player.getVideoSize().height;
    }

    @Override
    public boolean isPlaying() {
        return player.isPlaying();
    }

    @Override
    public void setTrack(Track item) {
        if (item.isSelected()) {
            ExoUtil.selectTrack(player, item.getGroup(), item.getTrack());
        } else {
            ExoUtil.deselectTrack(player, item.getGroup(), item.getTrack());
        }
    }
}
