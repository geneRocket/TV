package com.fongmi.android.tv.ui.player;

import com.fongmi.android.tv.utils.AppTaskScheduler;
import com.fongmi.android.tv.utils.TaskScheduler;

/** Maps playback phases to TV presentation effects without depending on an Activity or ViewBinding. */
public final class TvVodPlaybackStateBinder {

    private static final long READY_STABLE_MS = 300;
    private static final long SEEK_BOUNCE_WINDOW_MS = 1500;
    private static final long SEEK_BUFFERING_GRACE_MS = 300;

    public interface Port {
        boolean isBackground();
        boolean isBuffering();
        void beginPreparing();
        void clearPlaybackOverlay();
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
    private final Runnable bufferingCommit = this::commitBuffering;
    private final Port port;
    private final TaskScheduler scheduler;
    private long lastSeekAt;
    private boolean bufferingScheduled;
    private boolean pendingSeek;

    public TvVodPlaybackStateBinder(Port port) {
        this(port, AppTaskScheduler.get());
    }

    TvVodPlaybackStateBinder(Port port, TaskScheduler scheduler) {
        if (port == null) throw new IllegalArgumentException("port must not be null");
        if (scheduler == null) throw new IllegalArgumentException("scheduler must not be null");
        this.port = port;
        this.scheduler = scheduler;
    }

    public void onPreparing() {
        clear();
        port.beginPreparing();
        port.clearPlaybackOverlay();
        port.showProgress();
    }

    public void onBuffering() {
        cancelReadyCommit();
        if (pendingSeek) {
            if (!bufferingScheduled) {
                bufferingScheduled = true;
                scheduler.post(bufferingCommit, SEEK_BUFFERING_GRACE_MS);
            }
            return;
        }
        port.showProgress();
    }

    public void onReady() {
        cancelReadyCommit();
        cancelBufferingCommit();
        if (!pendingSeek || java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime()) - lastSeekAt >= SEEK_BOUNCE_WINDOW_MS) commitReady();
        else scheduler.post(readyCommit, READY_STABLE_MS);
    }

    public void onSeek() {
        pendingSeek = true;
        lastSeekAt = java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime());
        cancelBufferingCommit();
        cancelReadyCommit();
    }

    public void clear() {
        pendingSeek = false;
        cancelBufferingCommit();
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

    private void commitBuffering() {
        bufferingScheduled = false;
        if (!port.isBackground() && port.isBuffering()) port.showProgress();
    }

    private void cancelBufferingCommit() {
        scheduler.remove(bufferingCommit);
        bufferingScheduled = false;
    }

    private void cancelReadyCommit() {
        scheduler.remove(readyCommit);
    }
}
