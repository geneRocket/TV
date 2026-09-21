package com.fongmi.android.tv.ui.player;

import static org.junit.Assert.assertEquals;

import com.fongmi.android.tv.utils.TaskScheduler;

import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.Map;

public class TvVodPlaybackStateBinderTest {

    @Test
    public void seekDoesNotPretendToBuffer() {
        FakePort port = new FakePort();
        FakeScheduler scheduler = new FakeScheduler();
        TvVodPlaybackStateBinder binder = new TvVodPlaybackStateBinder(port, scheduler);

        binder.onSeek();

        assertEquals(0, port.progressShows);
        assertEquals(0, scheduler.size());
    }

    @Test
    public void transientSeekBufferingDoesNotFlashProgress() {
        FakePort port = new FakePort();
        FakeScheduler scheduler = new FakeScheduler();
        TvVodPlaybackStateBinder binder = new TvVodPlaybackStateBinder(port, scheduler);

        binder.onSeek();
        port.buffering = true;
        binder.onBuffering();
        port.buffering = false;
        binder.onReady();
        scheduler.runAll();

        assertEquals(0, port.progressShows);
    }

    @Test
    public void sustainedSeekBufferingShowsProgressOnce() {
        FakePort port = new FakePort();
        FakeScheduler scheduler = new FakeScheduler();
        TvVodPlaybackStateBinder binder = new TvVodPlaybackStateBinder(port, scheduler);

        binder.onSeek();
        port.buffering = true;
        binder.onBuffering();
        binder.onBuffering();
        scheduler.runAll();

        assertEquals(1, port.progressShows);
    }

    @Test
    public void preparingClearsStalePlaybackOverlayBeforeProgress() {
        FakePort port = new FakePort();
        FakeScheduler scheduler = new FakeScheduler();
        TvVodPlaybackStateBinder binder = new TvVodPlaybackStateBinder(port, scheduler);

        binder.onPreparing();

        assertEquals(1, port.overlayClears);
        assertEquals(1, port.progressShows);
    }

    @Test
    public void preparingNewEpisodeClearsPreviousSeekGracePeriod() {
        FakePort port = new FakePort();
        FakeScheduler scheduler = new FakeScheduler();
        TvVodPlaybackStateBinder binder = new TvVodPlaybackStateBinder(port, scheduler);
        binder.onSeek();
        binder.onPreparing();
        port.buffering = true;
        binder.onBuffering();
        assertEquals(2, port.progressShows);
        assertEquals(0, scheduler.size());
    }

    @Test
    public void clearCancelsDelayedReadyEffects() {
        FakePort port = new FakePort();
        FakeScheduler scheduler = new FakeScheduler();
        TvVodPlaybackStateBinder binder = new TvVodPlaybackStateBinder(port, scheduler);
        binder.onSeek();
        binder.onReady();
        binder.clear();
        scheduler.runAll();
        assertEquals(0, port.metadataUpdates);
    }

    private static final class FakeScheduler implements TaskScheduler {
        private final Map<Runnable, Long> tasks = new LinkedHashMap<>();

        @Override public void post(Runnable task, long delayMillis) { tasks.put(task, delayMillis); }
        @Override public void remove(Runnable task) { tasks.remove(task); }
        int size() { return tasks.size(); }

        void runAll() {
            while (!tasks.isEmpty()) {
                Runnable task = tasks.keySet().iterator().next();
                tasks.remove(task);
                task.run();
            }
        }
    }

    private static final class FakePort implements TvVodPlaybackStateBinder.Port {
        private boolean buffering;
        private int progressShows;
        private int overlayClears;
        private int metadataUpdates;

        @Override public boolean isBackground() { return false; }
        @Override public boolean isBuffering() { return buffering; }
        @Override public void showProgress() { progressShows++; }
        @Override public void beginPreparing() {}
        @Override public void clearPlaybackOverlay() { overlayClears++; }
        @Override public void stopContentSearch() {}
        @Override public void clearPlaybackTimeout() {}
        @Override public void updateMetadata() { metadataUpdates++; }
        @Override public void resetRecovery() {}
        @Override public void clearBrokenSources() {}
        @Override public void hideProgress() {}
        @Override public void resetTransientPlayerState() {}
        @Override public void applyDefaultTrack() {}
        @Override public void showTracks() {}
        @Override public void persistPlayerSelection() {}
        @Override public void updateVideoSize() {}
    }
}
