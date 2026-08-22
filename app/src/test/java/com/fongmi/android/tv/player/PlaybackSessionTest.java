package com.fongmi.android.tv.player;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PlaybackSessionTest {

    @Test
    public void reservedSessionIsConsumedByStartWithoutChangingId() {
        PlaybackSession session = new PlaybackSession();
        long requested = session.begin();
        assertEquals(requested, session.ensure());
    }

    @Test
    public void startWithoutReservationCreatesSession() {
        PlaybackSession session = new PlaybackSession();
        assertEquals(1, session.ensure());
        assertEquals(2, session.ensure());
    }

    @Test
    public void oldSessionEventsAreRejected() {
        PlaybackSession session = new PlaybackSession();
        long old = session.begin();
        long current = session.begin();
        assertFalse(session.isCurrent(old));
        assertTrue(session.isCurrent(current));
    }
}
