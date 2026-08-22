package com.fongmi.android.tv.player;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class PlaybackMarkersTest {

    @Test
    public void openingOnlyAcceptsFirstHalfOfKnownTimeline() {
        assertEquals(20, PlaybackMarkers.openingAt(20, 100));
        assertEquals(PlaybackMarkers.INVALID, PlaybackMarkers.openingAt(60, 100));
        assertEquals(PlaybackMarkers.INVALID, PlaybackMarkers.openingAt(0, -1));
    }

    @Test
    public void endingOnlyAcceptsSecondHalfOfKnownTimeline() {
        assertEquals(20, PlaybackMarkers.endingAt(80, 100));
        assertEquals(PlaybackMarkers.INVALID, PlaybackMarkers.endingAt(40, 100));
        assertEquals(PlaybackMarkers.INVALID, PlaybackMarkers.endingAt(120, 100));
    }
}
