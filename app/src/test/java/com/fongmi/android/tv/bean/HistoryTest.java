package com.fongmi.android.tv.bean;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class HistoryTest {

    @Test
    public void importedKeysUseLocalConfigIdWithoutChangingSiteOrVodIdentity() {
        assertEquals("site@@@video@@@9", History.keyForConfig("site@@@video@@@42", 9));
        assertEquals("site@@@video@@@9", History.keyForConfig("42@site@@@video@@@42", 9));
        assertEquals("site@@@video@@@9", History.keyForConfig("site@@@video", 9));
        assertEquals("site@@@video@@@part@@@9", History.keyForConfig("site@@@video@@@part@@@42", 9));
        assertEquals("site@@@video@@@9", History.keyForConfig("site@@@video@@@9", 9));
    }

    @Test
    public void cachedProgressAndPendingInsertsKeepHistoryInNewestFirstOrder() throws Exception {
        java.lang.reflect.Field field = com.fongmi.android.tv.repository.HistoryStorage.class.getDeclaredField("CACHE");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        java.util.Map<String, History> cache = (java.util.Map<String, History>) field.get(com.fongmi.android.tv.repository.PersistenceServices.history());
        java.lang.reflect.Method merge = com.fongmi.android.tv.repository.HistoryStorage.class.getDeclaredMethod("withCached", java.util.List.class, java.util.List.class);
        merge.setAccessible(true);
        History oldFirst = entry("first", 200);
        History oldSecond = entry("second", 100);
        History updatedSecond = entry("second", 300);
        History inserted = entry("inserted", 400);
        try {
            History.clearCache();
            cache.put("999@second", updatedSecond);
            cache.put("999@inserted", inserted);
            @SuppressWarnings("unchecked")
            java.util.List<History> merged = (java.util.List<History>) merge.invoke(com.fongmi.android.tv.repository.PersistenceServices.history(),
                    java.util.Collections.singletonList(999), java.util.Arrays.asList(oldFirst, oldSecond));

            assertEquals(java.util.Arrays.asList(inserted, updatedSecond, oldFirst), merged);
        } finally {
            History.clearCache();
        }
    }

    private static History entry(String key, long time) {
        History history = new History();
        history.setKey(key);
        history.setCid(999);
        history.setCreateTime(time);
        return history;
    }

    @Test public void absentBestMatchDoesNotReplaceExistingHistory() {
        History existing = new History();
        existing.setPosition(120_000);
        org.junit.Assert.assertFalse(History.isNewerOrBetterSameVersion(null, existing));
        org.junit.Assert.assertFalse(History.isNewerOrBetterSameVersion(null, null));
        org.junit.Assert.assertTrue(History.isNewerOrBetterSameVersion(existing, null));
    }

    @Test
    public void asyncSnapshotDoesNotWaitForDatabaseWriteLock() throws Exception {
        java.lang.reflect.Field field = com.fongmi.android.tv.repository.HistoryStorage.class.getDeclaredField("WRITE_LOCK");
        field.setAccessible(true);
        Object databaseLock = field.get(com.fongmi.android.tv.repository.PersistenceServices.history());
        java.lang.reflect.Field activeField = com.fongmi.android.tv.repository.HistoryStorage.class.getDeclaredField("ACTIVE_WRITES");
        activeField.setAccessible(true);
        @SuppressWarnings("unchecked")
        java.util.Set<String> active = (java.util.Set<String>) activeField.get(com.fongmi.android.tv.repository.PersistenceServices.history());
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
