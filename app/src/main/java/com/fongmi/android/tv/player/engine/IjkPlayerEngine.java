package com.fongmi.android.tv.player.engine;

import com.fongmi.android.tv.bean.Track;
import com.fongmi.android.tv.player.IjkUtil;

import tv.danmaku.ijk.media.player.IMediaPlayer;
import tv.danmaku.ijk.media.player.ui.IjkVideoView;

public final class IjkPlayerEngine implements PlayerEngine, IMediaPlayer.Listener {

    private final IjkVideoView player;
    private Listener listener;
    private long sessionId;
    private long requestedStartPosition;
    private EnginePhase phase = EnginePhase.IDLE;
    private boolean playWhenReady;
    private boolean pendingStartSeek;
    private boolean released;

    public IjkPlayerEngine(IjkVideoView view, int playerType, int render, int decode) {
        this.player = view.render(render).decode(decode);
        player.addListener(this);
        player.setPlayer(playerType);
    }

    public IjkVideoView player() {
        return player;
    }

    @Override
    public void prepare(EngineRequest request, Listener listener) {
        requireActive();
        this.sessionId = request.getSessionId();
        this.requestedStartPosition = request.getStartPosition();
        this.listener = listener;
        this.phase = EnginePhase.PREPARING;
        this.playWhenReady = true;
        this.pendingStartSeek = requestedStartPosition > 0;
        player.setMediaSource(IjkUtil.getSource(request.getHeaders(), request.getUrl()), request.getStartPosition());
        if (phase == EnginePhase.PREPARING) player.start();
    }

    @Override public void play() {
        requireActive();
        playWhenReady = true;
        player.start();
        if (player.isPlaying() && phase != EnginePhase.READY) {
            phase = EnginePhase.READY;
            emit(null);
        }
    }

    @Override public void pause() {
        requireActive();
        playWhenReady = false;
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
        playWhenReady = false;
        pendingStartSeek = false;
        phase = EnginePhase.STOPPED;
        int state = player.getPlaybackState();
        if (state != IjkVideoView.STATE_IDLE && state != IjkVideoView.STATE_ERROR) player.stop();
        emit(null);
    }

    @Override public void setSpeed(float speed) { requireActive(); player.setSpeed(speed); }

    @Override
    public void setTrack(Track track) {
        requireActive();
        if (track.isSelected()) player.selectTrack(track.getType(), track.getTrack());
        else player.deselectTrack(track.getType(), track.getTrack());
    }

    @Override
    public EngineSnapshot snapshot() {
        boolean starting = phase == EnginePhase.PREPARING;
        long position = starting ? requestedStartPosition : player.getCurrentPosition();
        long buffered = starting ? requestedStartPosition : player.getBufferedPosition();
        return new EngineSnapshot(sessionId, phase, position, player.getDuration(), buffered, player.isPlaying(), player.getSpeed(), player.getVideoWidth(), player.getVideoHeight());
    }

    @Override
    public void release() {
        if (released) return;
        released = true;
        phase = EnginePhase.RELEASED;
        player.release();
        listener = null;
    }

    @Override
    public void onPrepared(IMediaPlayer mp) {
        if (released || phase == EnginePhase.STOPPED) return;
        ensureStartPositionApplied();
        if (playWhenReady) player.start();
        else player.pause();
        phase = playWhenReady ? EnginePhase.READY : EnginePhase.PAUSED;
        emit(null);
    }

    @Override
    public void onCompletion(IMediaPlayer mp) {
        if (released || phase == EnginePhase.STOPPED) return;
        playWhenReady = false;
        phase = EnginePhase.ENDED;
        emit(null);
    }

    @Override
    public void onInfo(IMediaPlayer mp, int what, int extra) {
        if (released || phase == EnginePhase.STOPPED) return;
        switch (what) {
            case IMediaPlayer.MEDIA_INFO_BUFFERING_START:
                phase = EnginePhase.BUFFERING;
                emit(null);
                break;
            case IMediaPlayer.MEDIA_INFO_BUFFERING_END:
            case IMediaPlayer.MEDIA_INFO_AUDIO_RENDERING_START:
            case IMediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START:
            case IMediaPlayer.MEDIA_INFO_VIDEO_SEEK_RENDERING_START:
            case IMediaPlayer.MEDIA_INFO_AUDIO_SEEK_RENDERING_START:
                phase = player.isPlaying() ? EnginePhase.READY : EnginePhase.PAUSED;
                ensureStartPositionApplied();
                emit(null);
                break;
        }
    }

    @Override
    public boolean onError(IMediaPlayer mp, int what, int extra) {
        if (released || phase == EnginePhase.STOPPED) return true;
        playWhenReady = false;
        phase = EnginePhase.ERROR;
        emit(new IjkPlaybackException(what, extra));
        return true;
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

    public static final class IjkPlaybackException extends RuntimeException {

        private final int what;
        private final int extra;

        IjkPlaybackException(int what, int extra) {
            super("IJK playback failed: what=" + what + ", extra=" + extra);
            this.what = what;
            this.extra = extra;
        }

        public int getWhat() { return what; }

        public int getExtra() { return extra; }
    }
}
