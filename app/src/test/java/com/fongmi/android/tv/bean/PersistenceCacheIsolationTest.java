package com.fongmi.android.tv.bean;

import org.junit.After;
import org.junit.Test;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.junit.Assert.*;

public class PersistenceCacheIsolationTest {
    @After public void clearCaches() {
        Config.clearCache();
        Keep.clearCache();
        History.clearCache();
    }

    @Test public void unsavedConfigEditsDoNotChangeCachedMetadataOrIdentity() throws Exception {
        Config original = new Config();
        original.id(9001).type(0).url("https://example.com/config.json").name("Saved");
        cache(Config.class, original);

        original.url("https://example.com/draft.json").name("Unsaved");

        assertEquals("Saved", Config.find(9001).getName());
        assertEquals("https://example.com/config.json", Config.find("https://example.com/config.json", 0).getUrl());
    }

    @Test public void configListCacheOwnsSnapshotsOfItsElements() throws Exception {
        Config original = new Config();
        original.id(9002).type(1).url("https://example.com/live.json").name("Saved");
        Method cache = Config.class.getDeclaredMethod("cache", int.class, java.util.List.class);
        cache.setAccessible(true);
        cache.invoke(null, 1, Arrays.asList(original));

        original.name("Unsaved");

        assertEquals("Saved", Config.getAll(1).get(0).getName());
        Config.getAll(1).get(0).name("Another draft");
        assertEquals("Saved", Config.getAll(1).get(0).getName());
    }

    @Test public void unsavedFavoriteEditsDoNotChangeCachedRow() throws Exception {
        Keep original = new Keep();
        original.setCid(9003);
        original.setKey("site@@@movie@@@9003");
        original.setSiteName("Source");
        original.setVodName("Saved");
        cache(Keep.class, original);

        original.setVodName("Unsaved");
        original.setKey("other@@@movie@@@9003");

        Keep saved = Keep.find(9003, "site@@@movie@@@9003");
        assertEquals("Saved", saved.getVodName());
        assertEquals("site@@@movie@@@9003", saved.getKey());
    }

    @Test public void unsavedHistoryEditsCannotEraseCachedProgress() throws Exception {
        History original = new History();
        original.setCid(9004);
        original.setKey("site@@@movie@@@9004");
        original.setPosition(120_000);
        original.setCreateTime(100);
        cache(History.class, original);

        original.setPosition(0);
        original.setVodRemarks("Draft episode");

        History saved = History.find("site@@@movie@@@9004");
        assertEquals(120_000, saved.getPosition());
        assertEquals("", saved.getVodRemarks());
    }

    @Test public void rejectedHistoryWriteDoesNotExposeTheNewerCacheEntry() throws Exception {
        History latest = new History();
        latest.setCid(9005);
        latest.setKey("site@@@movie@@@9005");
        latest.setPosition(120_000);
        latest.setCreateTime(200);
        cache(History.class, latest);
        History stale = new History();
        stale.setCid(9005);
        stale.setKey(latest.getKey());
        stale.setCreateTime(100);

        History retained = cache(History.class, stale);
        assertEquals(120_000, retained.getPosition());
        retained.setPosition(0);
        assertEquals(120_000, History.find(latest.getKey()).getPosition());
    }

    private static <T> T cache(java.lang.Class<T> owner, T value) throws Exception {
        Method cache = owner.getDeclaredMethod("cache", owner);
        cache.setAccessible(true);
        return owner.cast(cache.invoke(null, value));
    }
}
