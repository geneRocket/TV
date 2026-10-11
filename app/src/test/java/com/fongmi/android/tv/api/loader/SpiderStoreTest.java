package com.fongmi.android.tv.api.loader;

import com.github.catvod.crawler.Spider;
import org.junit.Test;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

public class SpiderStoreTest {
    @Test public void clearingAllDiscardsOldInitialization() throws Exception { checkClear(true); }
    @Test public void clearingOneKeyDiscardsOldInitialization() throws Exception { checkClear(false); }

    private void checkClear(boolean all) throws Exception {
        SpiderStore store = new SpiderStore(Spider::destroy);
        AtomicInteger oldDestroyed = new AtomicInteger();
        Spider old = new Spider() { @Override public void destroy() { oldDestroyed.incrementAndGet(); } };
        Spider fresh = new Spider() { };
        CountDownLatch started = new CountDownLatch(1), release = new CountDownLatch(1);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<Spider> initializing = executor.submit(() -> store.getOrCreate("site", () -> {
                started.countDown();
                assertTrue(release.await(2, TimeUnit.SECONDS));
                return old;
            }));
            assertTrue(started.await(2, TimeUnit.SECONDS));
            if (all) store.clear(); else store.clear("site");
            assertSame(fresh, store.getOrCreate("site", () -> fresh));
            release.countDown();
            assertNull(initializing.get(2, TimeUnit.SECONDS));
            assertSame(fresh, store.get("site"));
            assertEquals(1, oldDestroyed.get());
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }
}
