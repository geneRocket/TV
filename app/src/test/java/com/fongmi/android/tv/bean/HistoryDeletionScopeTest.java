package com.fongmi.android.tv.bean;

import org.junit.Test;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;

public class HistoryDeletionScopeTest {
    @Test public void queuedDeletionUsesTheConfigurationsSelectedWhenConfirmed() {
        AtomicReference<List<String>> loaded = new AtomicReference<>(Collections.singletonList("https://old.example/config"));
        AtomicInteger cid = new AtomicInteger(10);
        List<String> deleted = new ArrayList<>();
        Runnable task = com.fongmi.android.tv.repository.HistoryStore.prepareDeleteLoaded(loaded::get, cid::get, (urls, fallback) -> deleted.addAll(urls));
        loaded.set(Collections.singletonList("https://new.example/config")); cid.set(20);
        task.run();
        assertEquals(Collections.singletonList("https://old.example/config"), deleted);
    }

    @Test public void queuedDeletionOwnsACopyOfItsUrlList() {
        List<String> loaded = new ArrayList<>(Arrays.asList("first", "second"));
        List<String> deleted = new ArrayList<>();
        Runnable task = com.fongmi.android.tv.repository.HistoryStore.prepareDeleteLoaded(() -> loaded, () -> 10, (urls, fallback) -> deleted.addAll(urls));
        loaded.clear(); loaded.add("other"); task.run();
        assertEquals(Arrays.asList("first", "second"), deleted);
    }

    @Test public void emptyLoadedListKeepsTheOriginalFallbackConfiguration() {
        AtomicInteger cid = new AtomicInteger(10);
        AtomicInteger deletedCid = new AtomicInteger();
        Runnable task = com.fongmi.android.tv.repository.HistoryStore.prepareDeleteLoaded(Collections::emptyList, cid::get, (urls, fallback) -> deletedCid.set(fallback));
        cid.set(20); task.run();
        assertEquals(10, deletedCid.get());
    }

    @Test public void preparingDeletionDoesNotPerformDatabaseWork() {
        AtomicInteger deletes = new AtomicInteger();
        Runnable task = com.fongmi.android.tv.repository.HistoryStore.prepareDeleteLoaded(() -> Collections.singletonList("config"), () -> 10,
                (urls, fallback) -> deletes.incrementAndGet());
        assertEquals(0, deletes.get()); task.run(); assertEquals(1, deletes.get());
    }
}
