package com.fongmi.android.tv.player;

import org.junit.Test;
import java.util.concurrent.atomic.AtomicLong;
import master.flame.danmaku.danmaku.model.DanmakuTimer;
import master.flame.danmaku.danmaku.model.android.DanmakuContext;
import master.flame.danmaku.danmaku.parser.BaseDanmakuParser;
import static org.junit.Assert.*;

public class DanmakuControllerTest {
    private final AtomicLong now = new AtomicLong(1_000);
    private final Output output = new Output();
    private final DanmakuController controller = new DanmakuController(true, () -> {}, now::get, output);

    @Test public void longPressAccelerationAndReleaseAdvanceEveryFrameWithoutRestarting() {
        controller.updatePlayingState(true, false, 10_000, 1);
        assertEquals(10_100, after(100));
        controller.applySpeed(3);
        for (int frame = 1; frame <= 10; frame++) assertEquals(10_100 + frame * 30, after(10));
        controller.applySpeed(5);
        assertEquals(10_900, after(100));
        controller.applySpeed(1);
        assertEquals(11_000, after(100));
        assertEquals(1, output.starts);
        assertEquals(0, output.seeks);
        assertEquals(0, output.pauses);
    }

    @Test public void fractionalSpeedDoesNotLoseTimeBetweenFrames() {
        controller.updatePlayingState(true, false, 0, 1.25f);
        for (int i = 0; i < 100; i++) after(1);
        assertEquals(125, after(0));
    }

    @Test public void repeatedReadyNotificationsDoNotClearRunningComments() {
        controller.updatePlayingState(true, false, 10_000, 3);
        now.addAndGet(100);
        controller.updatePlayingState(true, false, 10_300, 3);
        assertEquals(10_600, after(100));
        assertEquals(1, output.starts);
        assertEquals(0, output.resumes);
        assertEquals(0, output.seeks);
        assertEquals(1, output.shows);
    }

    @Test public void coarsePlayerSamplesCorrectDriftWithoutJumpingOrReversingComments() {
        controller.updatePlayingState(true, false, 10_000, 3);
        assertEquals(10_300, after(100));
        controller.updatePlayingState(true, false, 10_200, 3);
        assertEquals(10_300, after(0));
        assertEquals(10_560, after(100));
        assertEquals(13_200, after(900));
        controller.updatePlayingState(true, false, 0, 3);
        assertEquals(13_200, after(0));
        assertTrue(after(100) > 13_200);
        assertEquals(1, output.starts);
        assertEquals(0, output.seeks);
    }

    @Test public void highSpeedRenderingDoesNotRunSecondsAheadOfASlowerAudioClock() {
        controller.updatePlayingState(true, false, 0, 5);
        long mediaPosition = 0;
        for (int sample = 0; sample < 20; sample++) {
            long before = after(250);
            mediaPosition += 800;
            controller.updatePlayingState(true, false, mediaPosition, 5);
            assertEquals("Sampling must not teleport comments", before, after(0));
        }
        assertTrue("Render clock drifted ahead of actual playback", Math.abs(after(0) - mediaPosition) < 600);
    }

    @Test public void bufferingAndPauseFreezeTheTimelineAndResumeWithoutClearingComments() {
        controller.updatePlayingState(true, false, 10_000, 3);
        assertEquals(10_300, after(100));
        controller.updatePlayingState(true, true, 10_300, 3);
        assertEquals(10_300, after(2_000));
        controller.updatePlayingState(true, false, 10_300, 3);
        assertEquals(10_600, after(100));
        controller.pause();
        assertEquals(10_600, after(2_000));
        controller.applySpeed(1.5f);
        assertEquals(10_600, after(100));
        controller.updatePlayingState(true, false, 10_600, 1.5f);
        assertEquals(10_750, after(100));
        assertEquals(1, output.starts);
        assertEquals(2, output.resumes);
        assertEquals(0, output.seeks);
    }

    @Test public void hiddenCommentsCatchUpOnceWhenShownAgain() {
        controller.updatePlayingState(true, false, 10_000, 3);
        controller.setVisible(false, true, false, 10_000, 3);
        assertEquals(16_000, after(2_000));
        controller.setVisible(true, true, false, 16_000, 3);
        assertEquals(16_300, after(100));
        assertEquals(1, output.starts);
        assertEquals(1, output.seeks);
        assertEquals(1, output.resumes);
        assertEquals(1, output.hides);
    }

    @Test public void forwardAndBackwardSeekUseTheTargetEvenWhilePaused() {
        controller.updatePlayingState(true, false, 10_000, 3);
        controller.seekTo(60_000);
        assertEquals(60_300, after(100));
        controller.pause();
        controller.seekTo(5_000);
        assertEquals(5_000, after(100));
        controller.updatePlayingState(true, false, 5_000, 3);
        assertEquals(5_300, after(100));
        assertEquals(2, output.seeks);
    }

    @Test public void delayedPreparationStartsAtTheCurrentPlayerPositionAndSpeed() {
        output.ready = false;
        controller.updatePlayingState(true, false, 10_000, 3);
        after(1_000);
        assertEquals(0, output.starts);
        output.ready = true;
        controller.updatePlayingState(true, false, 13_000, 3);
        assertEquals(13_300, after(100));
        assertEquals(1, output.starts);
        assertEquals(13_000, output.startPosition);
    }

    @Test public void resetCannotLeakThePreviousEpisodesClockIntoTheNewOne() {
        controller.updatePlayingState(true, false, 60_000, 5);
        controller.reset();
        assertEquals(0, after(1_000));
        controller.updatePlayingState(true, false, 0, 1);
        assertEquals(100, after(100));
        assertEquals(2, output.starts);
        assertEquals(1, output.releases);
    }

    @Test public void stoppingAndDisposingFreezeTheClock() {
        controller.updatePlayingState(true, false, 10_000, 3);
        controller.stop();
        assertEquals(10_000, after(1_000));
        controller.release();
        assertEquals(0, after(1_000));
        assertEquals(1, output.stops);
    }

    @Test public void invalidSpeedCannotPoisonTheRenderTimer() {
        controller.updatePlayingState(true, false, 0, Float.NaN);
        assertEquals(100, after(100));
        controller.applySpeed(Float.POSITIVE_INFINITY);
        assertEquals(200, after(100));
        controller.applySpeed(-3);
        assertEquals(300, after(100));
    }

    private long after(long elapsed) {
        now.addAndGet(elapsed);
        // The library normally updates its own timer before this callback. The
        // callback must supply media time, not add another wall-time increment.
        DanmakuTimer timer = new DanmakuTimer(999_999);
        controller.updateTimer(timer);
        return timer.currMillisecond;
    }

    private static final class Output implements DanmakuController.Output {
        boolean ready = true;
        int starts, resumes, pauses, seeks, shows, hides, stops, releases;
        long startPosition;
        public boolean isPrepared() { return ready; }
        public void show() { shows++; }
        public void hide() { hides++; }
        public void pause() { pauses++; }
        public void resume() { resumes++; }
        public void start(long position) { starts++; startPosition = position; }
        public void seekTo(long position) { seeks++; }
        public void stop() { stops++; }
        public void release() { releases++; }
        public void prepare(BaseDanmakuParser parser, DanmakuContext context) {}
    }
}
