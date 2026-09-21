package com.fongmi.android.tv.player;

import org.junit.Test;
import static org.junit.Assert.*;

public class RemoteSeekAccumulatorTest {
    @Test public void oppositeTapReturnsToOriginalPosition() {
        RemoteSeekAccumulator seek = new RemoteSeekAccumulator();
        assertEquals(10_000, seek.step(true, 0, 10_000));
        assertEquals(20_000, seek.step(true, 0, 10_000));
        assertEquals(10_000, seek.step(false, 0, 10_000));
        assertEquals(0, seek.step(false, 0, 10_000));
    }
    @Test public void heldKeyAcceleratesButNewTapDoesNot() {
        RemoteSeekAccumulator seek = new RemoteSeekAccumulator();
        assertEquals(10_000, seek.step(true, 0, 10_000));
        assertEquals(30_000, seek.step(true, 10, 10_000));
        assertEquals(70_000, seek.step(true, 30, 10_000));
        assertEquals(80_000, seek.step(true, 0, 10_000));
        seek.reset();
        assertEquals(0, seek.value());
    }
}
