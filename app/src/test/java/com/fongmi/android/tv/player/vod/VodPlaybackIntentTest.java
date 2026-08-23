package com.fongmi.android.tv.player.vod;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class VodPlaybackIntentTest {

    @Test
    public void defaultPlaybackResumesSavedPosition() {
        VodPlaybackIntent intent = new VodPlaybackIntent();

        assertEquals(StartPolicy.RESUME_SAVED, intent.consumeStartPolicy(false, false));
    }

    @Test
    public void keepCurrentPositionIsConsumedOnce() {
        VodPlaybackIntent intent = new VodPlaybackIntent();
        intent.keepCurrentPositionOnNextPlay();

        assertTrue(intent.hasKeepCurrentPositionOnNextPlay());
        assertEquals(StartPolicy.KEEP_CURRENT_POSITION, intent.consumeStartPolicy(false, false));
        assertFalse(intent.hasKeepCurrentPositionOnNextPlay());
        assertEquals(StartPolicy.RESUME_SAVED, intent.consumeStartPolicy(false, false));
    }

    @Test
    public void replayTakesPrecedenceAndClearsPendingKeepPosition() {
        VodPlaybackIntent intent = new VodPlaybackIntent();
        intent.keepCurrentPositionOnNextPlay();

        assertEquals(StartPolicy.FRESH, intent.consumeStartPolicy(true, false));
        assertEquals(StartPolicy.RESUME_SAVED, intent.consumeStartPolicy(false, false));
    }

    @Test
    public void activeSourceSwitchKeepsCurrentPositionWithoutPendingIntent() {
        VodPlaybackIntent intent = new VodPlaybackIntent();

        assertEquals(StartPolicy.KEEP_CURRENT_POSITION, intent.consumeStartPolicy(false, true));
        assertEquals(StartPolicy.RESUME_SAVED, intent.consumeStartPolicy(false, false));
    }
}
