package com.fongmi.android.tv.player;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class PlaybackSeekGestureTest {

    @Test
    public void previewAndCommitUseSameOriginWhilePlaybackAdvances() {
        PlaybackSeekGesture gesture = new PlaybackSeekGesture();

        assertEquals(70_000, gesture.preview(60_000, 120_000, 10_000));
        assertEquals(80_000, gesture.preview(65_000, 120_000, 20_000));
        assertEquals(80_000, gesture.commit(70_000, 120_000, 20_000));
    }

    @Test
    public void commitWithoutPreviewUsesCurrentPosition() {
        PlaybackSeekGesture gesture = new PlaybackSeekGesture();

        assertEquals(40_000, gesture.commit(30_000, 120_000, 10_000));
    }

    @Test
    public void resetStartsANewInteraction() {
        PlaybackSeekGesture gesture = new PlaybackSeekGesture();
        gesture.preview(60_000, 120_000, 10_000);
        gesture.reset();

        assertEquals(100_000, gesture.preview(90_000, 120_000, 10_000));
    }
}
