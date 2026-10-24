package com.fongmi.android.tv.ui.player;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import com.fongmi.android.tv.utils.TaskScheduler;

import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.Map;

public class TvVodPlaybackStateBinderTest {
    @Test public void preparingAndBufferingNeverOverlapAnyExistingPlaybackOverlay() {
        for (boolean seek : new boolean[]{false, true}) {
            for (boolean preparing : new boolean[]{false, true}) {
                FakePort port = new FakePort();
                FakeScheduler scheduler = new FakeScheduler();
                TvVodPlaybackStateBinder binder = new TvVodPlaybackStateBinder(port, scheduler);
                if (seek) binder.onSeek();
                port.overlayVisible = true;
                port.buffering = true;
                if (preparing) binder.onPreparing(); else binder.onBuffering();
                scheduler.runAll();
                assertFalse(port.overlappingOverlays);
                assertFalse(port.overlayVisible);
                org.junit.Assert.assertTrue(port.progressVisible);
                binder.onPaused();
                assertFalse(port.progressVisible);
            }
        }
    }

    @Test public void leavingPageDuringDelayedSeekCannotUpdateMetadataOrShowLoadingLater() {
        for (boolean buffering : new boolean[]{false, true}) {
            FakePort port = new FakePort();
            FakeScheduler scheduler = new FakeScheduler();
            TvVodPlaybackStateBinder binder = new TvVodPlaybackStateBinder(port, scheduler);
            binder.onSeek();
            port.buffering = buffering;
            if (buffering) binder.onBuffering(); else binder.onReady();
            port.background = true;
            scheduler.runAll();
            assertEquals(0, port.metadataUpdates);
            assertEquals(0, port.progressShows);
            binder.release();
            assertEquals(0, scheduler.size());
        }
    }

    @Test public void rapidSeekBufferPauseThenPlaySettlesWithoutAnOldLoadingTask() {
        FakePort port = new FakePort();
        FakeScheduler scheduler = new FakeScheduler();
        TvVodPlaybackStateBinder binder = new TvVodPlaybackStateBinder(port, scheduler);
        for (int i = 0; i < 5; i++) {
            binder.onSeek();
            port.buffering = true;
            binder.onBuffering();
            binder.onPaused();
            port.buffering = false;
            binder.onReady();
        }
        scheduler.runAll();
        assertEquals(0, port.progressShows);
        assertFalse(port.overlappingOverlays);
        assertFalse(port.progressVisible);
        assertEquals(5, port.metadataUpdates);
    }

    @Test
    public void seekDuringExistingBufferingRestoresProgressWithoutAnotherStateEvent() {
        FakePort port = new FakePort();
        FakeScheduler scheduler = new FakeScheduler();
        TvVodPlaybackStateBinder binder = new TvVodPlaybackStateBinder(port, scheduler);
        port.buffering = true;
        port.overlayVisible = true;

        binder.onSeek();
        scheduler.runAll();

        assertEquals(1, port.progressShows);
        assertFalse(port.overlayVisible);
        assertFalse(port.overlappingOverlays);
    }

    @Test
    public void pausingWhileBufferingHidesProgress() {
        FakePort port = new FakePort();
        TvVodPlaybackStateBinder binder = new TvVodPlaybackStateBinder(port, new FakeScheduler());
        port.buffering = true;
        binder.onBuffering();

        binder.onPaused();

        assertFalse(port.progressVisible);
    }

    @Test
    public void pausingCancelsDelayedSeekBuffering() {
        FakePort port = new FakePort();
        FakeScheduler scheduler = new FakeScheduler();
        TvVodPlaybackStateBinder binder = new TvVodPlaybackStateBinder(port, scheduler);
        binder.onSeek();
        port.buffering = true;
        binder.onBuffering();

        binder.onPaused();
        port.overlayVisible = true;
        scheduler.runAll();

        assertEquals(0, port.progressShows);
        assertEquals(0, scheduler.size());
        assertFalse(port.overlappingOverlays);
    }

    @Test
    public void bufferingClearsPauseOverlayBeforeShowingProgress() {
        FakePort port = new FakePort();
        TvVodPlaybackStateBinder binder = new TvVodPlaybackStateBinder(port, new FakeScheduler());
        port.overlayVisible = true;

        binder.onBuffering();

        assertFalse(port.overlappingOverlays);
        assertFalse(port.overlayVisible);
        assertEquals(1, port.progressShows);
    }

    @Test
    public void sustainedSeekBufferingClearsSeekOverlayBeforeShowingProgress() {
        FakePort port = new FakePort();
        FakeScheduler scheduler = new FakeScheduler();
        TvVodPlaybackStateBinder binder = new TvVodPlaybackStateBinder(port, scheduler);
        binder.onSeek();
        port.overlayVisible = true;
        port.buffering = true;
        binder.onBuffering();

        scheduler.runAll();

        assertFalse(port.overlappingOverlays);
        assertFalse(port.overlayVisible);
        assertEquals(1, port.progressShows);
    }

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
        private boolean background;
        private int progressShows;
        private int overlayClears;
        private int metadataUpdates;
        private boolean overlayVisible;
        private boolean progressVisible;
        private boolean overlappingOverlays;

        @Override public boolean isBackground() { return background; }
        @Override public boolean isBuffering() { return buffering; }
        @Override public void showProgress() {
            overlappingOverlays |= overlayVisible;
            progressVisible = true;
            progressShows++;
        }
        @Override public void beginPreparing() {}
        @Override public void clearPlaybackOverlay() {
            overlayVisible = false;
            overlayClears++;
        }
        @Override public void stopContentSearch() {}
        @Override public void clearPlaybackTimeout() {}
        @Override public void updateMetadata() { metadataUpdates++; }
        @Override public void clearBrokenSources() {}
        @Override public void hideProgress() { progressVisible = false; }
        @Override public void resetTransientPlayerState() {}
        @Override public void applyDefaultTrack() {}
        @Override public void showTracks() {}
        @Override public void persistPlayerSelection() {}
        @Override public void updateVideoSize() {}
    }
}
