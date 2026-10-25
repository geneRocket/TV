package com.fongmi.android.tv.api.config;

import java.util.concurrent.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class ConfigRuntimeTest {
    @Test public void readersNeverObserveAHalfPublishedVodLivePair() throws Exception {
        ConfigRuntime.Snapshot original = ConfigRuntime.snapshot();
        VodConfig firstVod = new VodConfig(), secondVod = new VodConfig();
        LiveConfig firstLive = new LiveConfig(), secondLive = new LiveConfig();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            ConfigRuntime.publish(firstVod, firstLive);
            Future<?> writer = pool.submit(() -> {
                for (int i = 0; i < 20000; i++) {
                    if ((i & 1) == 0) ConfigRuntime.publish(firstVod, firstLive);
                    else ConfigRuntime.publish(secondVod, secondLive);
                }
            });
            Future<?> reader = pool.submit(() -> {
                for (int i = 0; i < 20000; i++) {
                    ConfigRuntime.Snapshot snapshot = ConfigRuntime.snapshot();
                    assertSame(snapshot.vod == firstVod ? firstLive : secondLive, snapshot.live);
                }
            });
            writer.get(5, TimeUnit.SECONDS); reader.get(5, TimeUnit.SECONDS);
        } finally { pool.shutdownNow(); ConfigRuntime.publish(original.vod, original.live); }
    }
    @Test public void replacingOnlyLivePreservesVodAndExistingSnapshotReferences() {
        ConfigRuntime.Snapshot original = ConfigRuntime.snapshot();
        try {
            LiveConfig replacement = new LiveConfig(); ConfigRuntime.publishLive(replacement);
            ConfigRuntime.Snapshot next = ConfigRuntime.snapshot();
            assertSame(original.vod, next.vod); assertSame(replacement, next.live);
            assertNotSame(original, next);
            assertNotSame(replacement, original.live);
        } finally { ConfigRuntime.publish(original.vod, original.live); }
    }
}
