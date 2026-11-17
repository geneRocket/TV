package com.fongmi.android.tv.player.engine;

import com.fongmi.android.tv.utils.AppTaskScheduler;
import com.fongmi.android.tv.utils.TaskScheduler;
import com.fongmi.android.tv.bean.Track;
import com.fongmi.android.tv.player.IjkUtil;

import tv.danmaku.ijk.media.player.IMediaPlayer;
import tv.danmaku.ijk.media.player.ui.IjkVideoView;

public final class IjkPlayerEngine implements PlayerEngine, IMediaPlayer.Listener {

    private static final long READY_FALLBACK_MS = 1000;

    private final IjkVideoView player;
    private final Runnable readyFallback;
    private final TaskScheduler scheduler;
    private Listener listener;
    private long sessionId;
    private long requestedStartPosition;
    private EnginePhase phase = EnginePhase.IDLE;
    private boolean playWhenReady;
    private boolean pendingStartSeek;
    private boolean pendingReady;
    private boolean released;

    public IjkPlayerEngine(IjkVideoView view, int playerType, int render, int decode) {
        this(view, playerType, render, decode, AppTaskScheduler.get());
    }

    IjkPlayerEngine(IjkVideoView view, int playerType, int render, int decode, TaskScheduler scheduler) {
        this.player = view.render(render).decode(decode);
        this.scheduler = scheduler;
        this.readyFallback = this::dispatchReadyFallback;
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
        this.playWhenReady = request.isPlayWhenReady();
        this.pendingStartSeek = requestedStartPosition > 0;
        this.pendingReady = true;
        scheduler.remove(readyFallback);
        scheduler.post(readyFallback, READY_FALLBACK_MS);
        player.setMediaSource(IjkUtil.getSource(request.getHeaders(), request.getUrl()), Math.max(request.getStartPosition(), 0), playWhenReady);
        if (phase == EnginePhase.PREPARING) { if (playWhenReady) player.start(); else player.pause(); }
    }

    @Override public void play() {
        requireActive();
        playWhenReady = true;
        player.start();
        if (player.isPlaying() && phase == EnginePhase.PAUSED) {
            phase = EnginePhase.READY;
            emit(null);
        }
    }

    @Override public void pause() {
        requireActive();
        playWhenReady = false;
        player.pause();
        if (phase == EnginePhase.READY) {
            phase = EnginePhase.PAUSED;
            emit(null);
        }
    }

    @Override public void seekTo(long position) {
        requireActive();
        requestedStartPosition = Math.max(position, 0);
        if (pendingReady) {
            pendingStartSeek = true;
            player.seekTo(requestedStartPosition);
            return;
        }
        pendingStartSeek = false;
        player.seekTo(requestedStartPosition);
    }

    @Override
    public void stop() {
        if (released) return;
        removeReadyFallback();
        playWhenReady = false;
        pendingStartSeek = false;
        pendingReady = false;
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
        boolean starting = pendingReady || pendingStartSeek || phase == EnginePhase.PREPARING;
        long position = starting ? requestedStartPosition : player.getCurrentPosition();
        long buffered = starting ? requestedStartPosition : player.getBufferedPosition();
        return new EngineSnapshot(sessionId, phase, position, player.getDuration(), buffered, player.isPlaying(), player.getSpeed(), player.getVideoWidth(), player.getVideoHeight());
    }

    @Override
    public void release() {
        if (released) return;
        released = true;
        removeReadyFallback();
        pendingReady = false;
        phase = EnginePhase.RELEASED;
        player.release();
        listener = null;
    }

    @Override
    public void onPrepared(IMediaPlayer mp) {
        if (released || phase == EnginePhase.STOPPED) return;
        removeReadyFallback();
        pendingReady = false;
        // IjkVideoView applies its pending seek before forwarding onPrepared to us.
        pendingStartSeek = false;
        // The view also starts playback after this callback when its target is playing.
        phase = playWhenReady ? EnginePhase.READY : EnginePhase.PAUSED;
        emit(null);
    }

    @Override
    public void onCompletion(IMediaPlayer mp) {
        if (released || phase == EnginePhase.STOPPED) return;
        removeReadyFallback();
        pendingReady = false;
        playWhenReady = false;
        phase = EnginePhase.ENDED;
        emit(null);
    }

    @Override
    public void onInfo(IMediaPlayer mp, int what, int extra) {
        if (released || phase == EnginePhase.STOPPED) return;
        switch (what) {
            case IMediaPlayer.MEDIA_INFO_BUFFERING_START:
                // A paused initial seek may never produce BUFFERING_END until
                // playback resumes. Native buffering must not override user pause.
                if (!playWhenReady) break;
                phase = EnginePhase.BUFFERING;
                emit(null);
                break;
            case IMediaPlayer.MEDIA_INFO_BUFFERING_END:
            case IMediaPlayer.MEDIA_INFO_AUDIO_RENDERING_START:
            case IMediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START:
            case IMediaPlayer.MEDIA_INFO_VIDEO_SEEK_RENDERING_START:
            case IMediaPlayer.MEDIA_INFO_AUDIO_SEEK_RENDERING_START:
                removeReadyFallback();
                pendingReady = false;
                phase = player.isPlaying() ? EnginePhase.READY : EnginePhase.PAUSED;
                ensureStartPositionApplied();
                emit(null);
                break;
        }
    }

    @Override
    public void onBufferingUpdate(IMediaPlayer mp, int percent) {
    }

    @Override
    public boolean onError(IMediaPlayer mp, int what, int extra) {
        if (released || phase == EnginePhase.STOPPED) return true;
        removeReadyFallback();
        playWhenReady = false;
        pendingReady = false;
        phase = EnginePhase.ERROR;
        emit(new IjkPlaybackException(what, extra));
        return true;
    }

    private void dispatchReadyFallback() {
        if (!pendingReady || released || phase == EnginePhase.STOPPED) return;
        int state = player.getPlaybackState();
        if (state == IjkVideoView.STATE_IDLE || state == IjkVideoView.STATE_PREPARING || state == IjkVideoView.STATE_ERROR) return;
        pendingReady = false;
        phase = playWhenReady ? EnginePhase.READY : EnginePhase.PAUSED;
        emit(null);
    }

    private void removeReadyFallback() {
        scheduler.remove(readyFallback);
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
