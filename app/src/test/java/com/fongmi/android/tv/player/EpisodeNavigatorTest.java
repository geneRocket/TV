package com.fongmi.android.tv.player;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class EpisodeNavigatorTest {

    @Test
    public void navigationClampsAtEpisodeBoundaries() {
        assertEquals(1, EpisodeNavigator.next(0, 3));
        assertEquals(2, EpisodeNavigator.next(2, 3));
        assertEquals(0, EpisodeNavigator.previous(0, 3));
        assertEquals(1, EpisodeNavigator.previous(2, 3));
        assertEquals(-1, EpisodeNavigator.next(0, 0));
    }
}
