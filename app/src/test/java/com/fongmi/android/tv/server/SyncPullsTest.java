package com.fongmi.android.tv.server;

import org.junit.Test;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.Assert.*;

public class SyncPullsTest {
    private final AtomicLong now = new AtomicLong();
    private final SyncPulls pulls = new SyncPulls(now::get);

    @Test public void overwriteIntentIsConsumedOnceByTheCallbackPeer() {
        pulls.begin("http://192.0.2.10:9978", "history", "https://config.example/a");
        assertTrue(pulls.consume("192.0.2.10", "history", "https://config.example/a"));
        assertFalse(pulls.consume("192.0.2.10", "history", "https://config.example/a"));
    }

    @Test public void anotherConfigurationDoesNotConsumeTheHistoryOverwrite() {
        pulls.begin("http://192.0.2.10:9978", "history", "https://config.example/a");
        assertFalse(pulls.consume("192.0.2.10", "history", "https://config.example/b"));
        assertTrue(pulls.consume("192.0.2.10", "history", "https://config.example/a"));
    }

    @Test public void anotherPeerOrRecordTypeCannotUseTheIntent() {
        pulls.begin("http://192.0.2.10:9978", "keep", "");
        assertFalse(pulls.consume("192.0.2.11", "keep", ""));
        assertFalse(pulls.consume("192.0.2.10", "history", "https://config.example/a"));
        assertTrue(pulls.consume("192.0.2.10", "keep", ""));
    }

    @Test public void failedOrCancelledControlRequestDisarmsTheOverwrite() {
        SyncPulls.Ticket ticket = pulls.begin("http://192.0.2.10:9978", "keep", "");
        ticket.close();
        assertFalse(pulls.consume("192.0.2.10", "keep", ""));
    }

    @Test public void cancellingOldRequestCannotDisarmItsReplacement() {
        SyncPulls.Ticket old = pulls.begin("http://192.0.2.10:9978", "keep", "");
        pulls.begin("http://192.0.2.10:9978", "keep", "");
        old.close();
        assertTrue(pulls.consume("192.0.2.10", "keep", ""));
    }

    @Test public void expiredIntentCannotOverwriteALaterUnrelatedSync() {
        pulls.begin("http://192.0.2.10:9978", "keep", "");
        now.set(SyncPulls.MAX_AGE_NANOS + 1);
        assertFalse(pulls.consume("192.0.2.10", "keep", ""));
    }

    @Test public void ipv6CallbackAddressMatchesTheDiscoveredDeviceUrl() {
        pulls.begin("http://[2001:db8::1]:9978", "keep", "");
        assertTrue(pulls.consume("2001:0db8:0:0:0:0:0:1", "keep", ""));
    }

    @Test public void boundedPendingRequestsEvictOnlyTheOldestIntent() {
        for (int i = 0; i < 65; i++) pulls.begin("http://192.0.2.10:9978", "history", "https://config.example/" + i);
        assertFalse(pulls.consume("192.0.2.10", "history", "https://config.example/0"));
        assertTrue(pulls.consume("192.0.2.10", "history", "https://config.example/64"));
    }
}
