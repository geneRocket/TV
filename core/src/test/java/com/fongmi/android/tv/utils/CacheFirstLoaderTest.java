package com.fongmi.android.tv.utils;

import org.junit.Test;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

public class CacheFirstLoaderTest {
    @Test public void expiredAndMissingInputsDoNotDelayCachedStartup() throws Exception {
        ExecutorService caller = Executors.newSingleThreadExecutor();
        ExecutorService refresh = Executors.newFixedThreadPool(2);
        CountDownLatch started = new CountDownLatch(2), finish = new CountDownLatch(1);
        try {
            Future<List<String>> result = caller.submit(() -> CacheFirstLoader.load(
                    Arrays.asList("expired", "missing"), key -> key.equals("expired") ? "old payload" : null,
                    key -> true, key -> refresh.execute(() -> {
                        started.countDown();
                        try { finish.await(); } catch (InterruptedException error) { Thread.currentThread().interrupt(); }
                    }), () -> { throw new AssertionError("Startup waited for network"); }));
            assertEquals(Collections.singletonList("old payload"), result.get(1, TimeUnit.SECONDS));
            assertTrue(started.await(1, TimeUnit.SECONDS));
            assertEquals(1, finish.getCount());
        } finally { finish.countDown(); caller.shutdownNow(); refresh.shutdownNow(); }
    }

    @Test public void freshCacheDoesNotRefresh() {
        List<String> result = CacheFirstLoader.load(Collections.singletonList("fresh"), key -> "cached",
                key -> false, key -> { throw new AssertionError("Fresh cache fetched again"); },
                () -> { throw new AssertionError("Cache ignored"); });
        assertEquals(Collections.singletonList("cached"), result);
    }

    @Test public void partialCacheKeepsInputOrderAndRefreshesOnlyMissingOrExpiredInputs() {
        List<String> updates = new ArrayList<>();
        List<String> result = CacheFirstLoader.load(Arrays.asList("first", "missing", "last"),
                key -> key.equals("missing") ? null : key, key -> key.equals("last"), updates::add,
                () -> { throw new AssertionError("Partial cache ignored"); });
        assertEquals(Arrays.asList("first", "last"), result);
        assertEquals(Arrays.asList("missing", "last"), updates);
    }

    @Test public void noUsableCacheFetchesOnceWithoutDuplicateRefreshes() {
        AtomicInteger fetches = new AtomicInteger();
        List<String> result = CacheFirstLoader.load(Arrays.asList("missing", "invalid"), key -> null,
                key -> true, key -> { throw new AssertionError("Duplicate fetch scheduled"); },
                () -> { fetches.incrementAndGet(); return Arrays.asList("new first", "new last"); });
        assertEquals(Arrays.asList("new first", "new last"), result);
        assertEquals(1, fetches.get());
    }

    @Test public void backgroundUpdateDoesNotReplaceTheReturnedSnapshot() {
        Map<String, String> cache = new HashMap<>();
        cache.put("source", "old");
        List<Runnable> work = new ArrayList<>();
        List<String> result = CacheFirstLoader.load(Collections.singletonList("source"), cache::get,
                key -> true, key -> work.add(() -> cache.put(key, "new")), Collections::emptyList);
        work.get(0).run();
        assertEquals("new", cache.get("source"));
        assertEquals(Collections.singletonList("old"), result);
    }
    @Test public void missingInputIsFetchedAndPublishedDuringTheSameStartup() {
        Map<String, String> cache = new HashMap<>();
        cache.put("expired", "old");
        List<String> active = new ArrayList<>();
        List<Runnable> work = new ArrayList<>();
        List<String> initial = CacheFirstLoader.load(Arrays.asList("expired", "missing"), cache::get,
                key -> true, key -> work.add(() -> cache.put(key, "refreshed")),
                key -> work.add(() -> { cache.put(key, "fetched"); active.add(cache.get(key)); }),
                () -> { throw new AssertionError("Cached startup must not wait"); });
        active.addAll(initial);
        assertEquals(Collections.singletonList("old"), active);
        work.get(0).run();
        assertEquals(Collections.singletonList("old"), active);
        work.get(1).run();
        assertEquals(Arrays.asList("old", "fetched"), active);
        assertEquals("fetched", cache.get("missing"));
    }

    @Test public void noCacheDoesNotAlsoScheduleMissingInputs() {
        AtomicInteger fetches = new AtomicInteger();
        List<String> values = CacheFirstLoader.load(Collections.singletonList("missing"), key -> null,
                key -> true, key -> { throw new AssertionError("No expired cache"); },
                key -> { throw new AssertionError("Fetch scheduled twice"); },
                () -> { fetches.incrementAndGet(); return Collections.singletonList("fetched"); });
        assertEquals(Collections.singletonList("fetched"), values);
        assertEquals(1, fetches.get());
    }
}
