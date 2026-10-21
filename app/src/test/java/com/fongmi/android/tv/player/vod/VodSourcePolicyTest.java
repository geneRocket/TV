package com.fongmi.android.tv.player.vod;

import org.junit.Test;

import static com.fongmi.android.tv.player.vod.VodSourcePolicy.Decision.*;
import static org.junit.Assert.*;

public class VodSourcePolicyTest {
    @Test public void pendingAutomaticSearchWaitsForThresholdEvenWhileSwitching() {
        assertEquals(WAIT, VodSourcePolicy.decide(false, false, true, true, true, true, 1, false));
        assertEquals(SELECT, VodSourcePolicy.decide(false, false, true, true, true, true, 10, true));
        assertEquals(SELECT, VodSourcePolicy.decide(true, false, false, true, true, true, 1, false));
    }

    @Test public void allCandidateSnapshotsRespectLoadingAndEmptySelectionGuards() {
        for (int bits = 0; bits < 128; bits++) {
            boolean settled = (bits & 1) != 0, loading = (bits & 2) != 0, pending = (bits & 4) != 0;
            boolean first = (bits & 8) != 0, auto = (bits & 16) != 0, switching = (bits & 32) != 0, threshold = (bits & 64) != 0;
            for (int count : new int[]{0, 1, 10}) {
                VodSourcePolicy.Decision decision = VodSourcePolicy.decide(settled, loading, pending, first, auto, switching, count, threshold);
                if (loading || (settled && pending)) assertEquals(WAIT, decision);
                if (count == 0) assertNotEquals(SELECT, decision);
                if (first && pending && !threshold) assertNotEquals(SELECT, decision);
                if (decision == FINISH_MANUAL) assertFalse(auto);
                if (decision == EMPTY_AUTOMATIC) { assertTrue(auto); assertEquals(0, count); }
            }
        }
    }
}
