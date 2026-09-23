package com.fongmi.android.tv.bean;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class HistoryTest {

    @Test
    public void asyncSnapshotDoesNotWaitForDatabaseWriteLock() throws Exception {
        java.lang.reflect.Field field = History.class.getDeclaredField("WRITE_LOCK");
        field.setAccessible(true);
        Object databaseLock = field.get(null);
        java.lang.reflect.Field activeField = History.class.getDeclaredField("ACTIVE_WRITES");
        activeField.setAccessible(true);
        @SuppressWarnings("unchecked")
        java.util.Set<String> active = (java.util.Set<String>) activeField.get(null);
        // Model an existing writer waiting on the database; don't launch Android threads.
        String writeKey = "999@nonblocking-history-test";
        active.add(writeKey);
        java.util.concurrent.ExecutorService caller = java.util.concurrent.Executors.newSingleThreadExecutor();
        History history = new History();
        history.setKey("nonblocking-history-test");
        history.setCid(999);
        history.setPosition(120_000);
        try {
            synchronized (databaseLock) {
                try {
                    caller.submit(history::saveAsync).get(2, java.util.concurrent.TimeUnit.SECONDS);
                } finally {
                    // Cancel while owning the DB lock so this unit test never writes Room.
                    History.cancelPendingWrites();
                }
            }
        } finally {
            caller.shutdownNow();
            active.remove(writeKey);
            History.cancelPendingWrites();
            History.clearCache();
        }
    }

    @Test
    public void historyKeyUsesVodIdWhenAvailable() {
        assertEquals("siteA@@@123@@@7", History.historyKey("siteA", "123", "Movie Name", 7));
    }

    @Test
    public void historyKeyFallsBackToNormalizedNameWhenVodIdIsEmpty() {
        assertEquals("siteA@@@movie name@@@7", History.historyKey("siteA", "", "Movie Name", 7));
    }

    @Test
    public void historyKeyStoresRawSiteKeyAndConfigId() {
        assertEquals("siteA@@@123@@@7", History.historyKey("7@siteA", "123", "Movie Name", 7));
    }

    @Test
    public void vodIdUsesStableFallbackWhenHistoryKeyUsesNameFallback() {
        History history = new History();
        history.setKey(History.historyKey("siteA", "", "Movie Name", 7));
        history.setVodName("Movie Name");

        assertEquals("movie name", history.getVodId());
    }
}
