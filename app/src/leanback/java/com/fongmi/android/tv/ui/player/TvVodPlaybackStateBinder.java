package com.fongmi.android.tv.ui.player;

import com.fongmi.android.tv.App;

/** Maps playback phases to TV presentation effects without depending on an Activity or ViewBinding. */
public final class TvVodPlaybackStateBinder {

    private static final long READY_STABLE_MS = 300;
    private static final long SEEK_BOUNCE_WINDOW_MS = 1500;

    public interface Port {
        boolean isBackground();
        boolean isBuffering();
        void beginPreparing();
        void showProgress();
        void stopContentSearch();
        void clearPlaybackTimeout();
        void updateMetadata();
        void resetRecovery();
        void clearBrokenSources();
        void hideProgress();
        void resetTransientPlayerState();
        void applyDefaultTrack();
        void showTracks();
        void persistPlayerSelection();
        void updateVideoSize();
    }

    private final Runnable readyCommit = this::commitReady;
    private final Port port;
    private long lastSeekAt;
    private boolean pendingSeek;

    public TvVodPlaybackStateBinder(Port port) {
        this.port = port;
    }

    public void onPreparing() {
        cancelReadyCommit();
        port.beginPreparing();
        port.showProgress();
    }

    public void onBuffering() {
        cancelReadyCommit();
        port.showProgress();
    }

    public void onReady() {
        if (!pendingSeek || System.currentTimeMillis() - lastSeekAt >= SEEK_BOUNCE_WINDOW_MS) commitReady();
        else App.post(readyCommit, READY_STABLE_MS);
    }

    public void onSeek() {
        pendingSeek = true;
        lastSeekAt = System.currentTimeMillis();
        cancelReadyCommit();
    }

    public void clear() {
        pendingSeek = false;
        cancelReadyCommit();
    }

    public void release() {
        clear();
    }

    private void commitReady() {
        if (port.isBackground() || port.isBuffering()) return;
        pendingSeek = false;
        port.stopContentSearch();
        port.clearPlaybackTimeout();
        port.updateMetadata();
        port.resetRecovery();
        port.clearBrokenSources();
        port.hideProgress();
        port.resetTransientPlayerState();
        port.applyDefaultTrack();
        port.showTracks();
        port.persistPlayerSelection();
        port.updateVideoSize();
    }

    private void cancelReadyCommit() {
        App.removeCallbacks(readyCommit);
    }
}
