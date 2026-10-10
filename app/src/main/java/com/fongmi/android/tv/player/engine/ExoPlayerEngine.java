package com.fongmi.android.tv.player.engine;

import androidx.annotation.NonNull;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.bean.Track;
import com.fongmi.android.tv.player.exo.ExoUtil;
import com.fongmi.android.tv.utils.UrlUtil;

public final class ExoPlayerEngine implements PlayerEngine, Player.Listener {

    private final ExoPlayer player;
    private final PlayerView view;
    private final boolean tunnel;
    private final int decode;
    private Listener listener;
    private long sessionId;
    private long requestedStartPosition;
    private EnginePhase phase = EnginePhase.IDLE;
    private boolean pendingStartSeek;
    private boolean pendingReady;
    private boolean released;

    public ExoPlayerEngine(PlayerView view, int decode, boolean playWithOthers, boolean tunnel) {
        this.view = view;
        this.decode = decode;
        this.tunnel = tunnel;
        this.player = new ExoPlayer.Builder(App.get())
                .setLoadControl(ExoUtil.buildLoadControl())
                .setTrackSelector(ExoUtil.buildTrackSelector())
                .setRenderersFactory(ExoUtil.buildRenderersFactory(decode))
                .setMediaSourceFactory(ExoUtil.buildMediaSourceFactory())
                .setVideoChangeFrameRateStrategy(C.VIDEO_CHANGE_FRAME_RATE_STRATEGY_ONLY_IF_SEAMLESS)
                .build();
        player.setAudioAttributes(AudioAttributes.DEFAULT, !playWithOthers);
        player.setHandleAudioBecomingNoisy(true);
        player.setPlayWhenReady(false);
        player.addListener(this);
        view.setPlayer(player);
    }

    public ExoPlayer player() {
        return player;
    }

    @Override
    public void prepare(EngineRequest request, Listener listener) {
        requireActive();
        this.sessionId = request.getSessionId();
        this.requestedStartPosition = request.getStartPosition();
        this.listener = listener;
        this.phase = EnginePhase.PREPARING;
        this.pendingStartSeek = requestedStartPosition > 0;
        this.pendingReady = true;
        MediaItem item = ExoUtil.getMediaItem(request.getHeaders(), UrlUtil.uri(request.getUrl()), request.getFormat(), request.getDrm(), request.getSubtitles(), decode, request.isForceLive());
        player.setPlayWhenReady(request.isPlayWhenReady());
        player.setMediaItem(item, request.getStartPosition());
        if (requestedStartPosition > 0) player.seekTo(requestedStartPosition);
        player.prepare();
    }

    @Override public void play() { requireActive(); player.play(); }

    @Override public void pause() {
        requireActive();
        player.pause();
        if (phase != EnginePhase.PAUSED) {
            phase = EnginePhase.PAUSED;
            emit(null);
        }
    }

    @Override public void seekTo(long position) {
        requireActive();
        pendingStartSeek = false;
        player.seekTo(Math.max(position, 0));
    }

    @Override
    public void stop() {
        if (released) return;
        pendingStartSeek = false;
        pendingReady = false;
        phase = EnginePhase.STOPPED;
        player.stop();
        player.clearMediaItems();
        emit(null);
    }

    @Override public void setSpeed(float speed) { requireActive(); if (!tunnel) player.setPlaybackSpeed(speed); }

    @Override
    public void setTrack(Track track) {
        requireActive();
        if (track.isSelected()) ExoUtil.selectTrack(player, track.getGroup(), track.getTrack());
        else ExoUtil.deselectTrack(player, track.getGroup(), track.getTrack());
    }

    @Override
    public EngineSnapshot snapshot() {
        boolean starting = phase == EnginePhase.PREPARING;
        long position = starting ? requestedStartPosition : player.getCurrentPosition();
        long buffered = starting ? requestedStartPosition : player.getBufferedPosition();
        return new EngineSnapshot(sessionId, phase, position, player.getDuration(), buffered, player.isPlaying(), player.getPlaybackParameters().speed, player.getVideoSize().width, player.getVideoSize().height);
    }

    @Override
    public void release() {
        if (released) return;
        released = true;
        pendingReady = false;
        phase = EnginePhase.RELEASED;
        player.removeListener(this);
        player.stop();
        if (view.getPlayer() == player) view.setPlayer(null);
        player.clearVideoSurface();
        player.release();
        listener = null;
    }

    @Override
    public void onPlaybackStateChanged(int state) {
        switch (state) {
            case Player.STATE_IDLE:
                pendingReady = false;
                if (phase == EnginePhase.PREPARING || phase == EnginePhase.STOPPED) return;
                phase = EnginePhase.IDLE;
                break;
            case Player.STATE_BUFFERING: phase = EnginePhase.BUFFERING; break;
            case Player.STATE_READY:
                ensureStartPositionApplied();
                if (pendingReady && player.getPlayWhenReady()) return;
                pendingReady = false;
                phase = player.isPlaying() ? EnginePhase.READY : EnginePhase.PAUSED;
                break;
            case Player.STATE_ENDED:
                pendingReady = false;
                phase = EnginePhase.ENDED;
                break;
            default: return;
        }
        emit(null);
    }

    @Override
    public void onIsPlayingChanged(boolean isPlaying) {
        if (isPlaying && pendingReady) {
            pendingReady = false;
            phase = EnginePhase.READY;
            emit(null);
        } else if (player.getPlaybackState() == Player.STATE_READY) {
            phase = isPlaying ? EnginePhase.READY : EnginePhase.PAUSED;
            emit(null);
        }
    }

    @Override
    public void onRenderedFirstFrame() {
        if (pendingReady) {
            pendingReady = false;
            phase = player.isPlaying() ? EnginePhase.READY : EnginePhase.PAUSED;
            emit(null);
        }
    }

    @Override
    public void onPlayerError(@NonNull PlaybackException error) {
        if (released || phase == EnginePhase.STOPPED) return;
        pendingReady = false;
        phase = EnginePhase.ERROR;
        emit(error);
    }

    private void emit(Throwable error) {
        if (listener == null || released) return;
        EngineSnapshot snapshot = snapshot();
        listener.onEngineEvent(error == null
                ? EngineEvent.state(sessionId, phase, snapshot.getPosition(), snapshot.getDuration())
                : EngineEvent.error(sessionId, error, snapshot.getPosition(), snapshot.getDuration()));
    }

    private void ensureStartPositionApplied() {
        if (!pendingStartSeek) return;
        pendingStartSeek = false;
        player.seekTo(requestedStartPosition);
    }

    private void requireActive() {
        if (released) throw new IllegalStateException("engine is released");
    }
}
