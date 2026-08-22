package com.fongmi.android.tv.player;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class PlaybackSeekTest {

    @Test
    public void relativeOffsetIsConvertedToAbsolutePosition() {
        assertEquals(2_400_000, PlaybackSeek.target(1_800_000, 3_600_000, 600_000));
        assertEquals(1_200_000, PlaybackSeek.target(1_800_000, 3_600_000, -600_000));
    }

    @Test
    public void targetIsClampedToTimeline() {
        assertEquals(0, PlaybackSeek.target(30_000, 3_600_000, -60_000));
        assertEquals(3_600_000, PlaybackSeek.target(3_500_000, 3_600_000, 600_000));
    }

    @Test
    public void unknownDurationStillAllowsForwardSeek() {
        assertEquals(90_000, PlaybackSeek.target(30_000, -1, 60_000));
    }
}
