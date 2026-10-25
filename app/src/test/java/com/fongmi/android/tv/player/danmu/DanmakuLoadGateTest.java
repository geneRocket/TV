package com.fongmi.android.tv.player.danmu;

import org.junit.Test;
import static org.junit.Assert.*;

public class DanmakuLoadGateTest {
    @Test public void repeatedReconciliationKeepsTheOriginalPendingAndPreparedLoad() {
        DanmakuLoadGate gate = new DanmakuLoadGate();
        long first = gate.begin(1, "proxy?do=diydanmu");
        for (int i = 0; i < 10; i++) {
            assertEquals(0, gate.begin(1, "proxy?do=diydanmu"));
            assertTrue(gate.accepts(first));
        }
    }

    @Test public void nextEpisodeMustReloadEvenWhenTheProxyUrlIsIdentical() {
        DanmakuLoadGate gate = new DanmakuLoadGate();
        long first = gate.begin(1, "proxy?do=diydanmu");
        long second = gate.begin(2, "proxy?do=diydanmu");
        assertNotEquals(0, second);
        assertFalse(gate.accepts(first));
        assertTrue(gate.accepts(second));
    }

    @Test public void selectingAnotherSourceDropsLateCallbacksFromTheFirst() {
        DanmakuLoadGate gate = new DanmakuLoadGate();
        long first = gate.begin(1, "source-a");
        long second = gate.begin(1, "source-b");
        assertFalse(gate.accepts(first));
        assertTrue(gate.accepts(second));
        gate.failed(first);
        assertEquals(0, gate.begin(1, "source-b"));
        assertTrue(gate.accepts(second));
    }

    @Test public void failureAllowsRetryButTheFailedRequestCannotPublishAgain() {
        DanmakuLoadGate gate = new DanmakuLoadGate();
        long first = gate.begin(1, "source");
        gate.failed(first);
        assertFalse(gate.accepts(first));
        long retry = gate.begin(1, "source");
        assertNotEquals(0, retry);
        assertTrue(gate.accepts(retry));
    }

    @Test public void disablingClearingOrManuallyRefreshingPermitsANewLoad() {
        DanmakuLoadGate gate = new DanmakuLoadGate();
        long first = gate.begin(1, "source");
        gate.invalidate();
        assertFalse(gate.accepts(first));
        assertNotEquals(0, gate.begin(1, "source"));
    }

    @Test public void removingTheSourceInvalidatesPendingWork() {
        DanmakuLoadGate gate = new DanmakuLoadGate();
        long first = gate.begin(1, "source");
        assertEquals(0, gate.begin(1, ""));
        assertFalse(gate.accepts(first));
        assertNotEquals(0, gate.begin(1, "source"));
    }
}
