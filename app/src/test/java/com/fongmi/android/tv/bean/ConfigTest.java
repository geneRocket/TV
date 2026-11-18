package com.fongmi.android.tv.bean;

import com.fongmi.android.tv.db.dao.ConfigDao;

import org.junit.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

public class ConfigTest {
    @Test
    public void ignoredInsertReturnsTheAlreadyPersistedIdentity() {
        Config winner = new Config().id(2718).url("https://example.test/first-config");
        Config loser = new Config().url(winner.getUrl());
        try {
            assertEquals(2718, loser.insert(new ConcurrentInsertDao(winner)).getId());
            assertEquals(2718, Config.find(winner.getUrl(), 0).getId());
        } finally {
            Config.clearCache();
        }
    }

    private static final class ConcurrentInsertDao extends ConfigDao {
        private final Config winner;

        ConcurrentInsertDao(Config winner) { this.winner = winner; }

        @Override public Long insert(Config item) { return -1L; }
        @Override public Config find(String url, int type) { return winner; }
        @Override public List<Config> findByType(int type) { throw new UnsupportedOperationException(); }
        @Override public List<Config.UrlItem> findUrlByType(int type) { throw new UnsupportedOperationException(); }
        @Override public Config findById(int id) { throw new UnsupportedOperationException(); }
        @Override public Config findOne(int type) { throw new UnsupportedOperationException(); }
        @Override public int rename(String url, String name, int type) { throw new UnsupportedOperationException(); }
        @Override public int commitUrlEdit(int id, String old, String url, String name, String json, long time) { throw new UnsupportedOperationException(); }
        @Override public void delete(String url, int type) { throw new UnsupportedOperationException(); }
        @Override public void delete(String url) { throw new UnsupportedOperationException(); }
        @Override public void delete(int type) { throw new UnsupportedOperationException(); }
        @Override public void updateHome(int id, String home) { throw new UnsupportedOperationException(); }
        @Override public void updateParse(int id, String parse) { throw new UnsupportedOperationException(); }
        @Override public int updateCache(int id, String url, int type, String json, long time, long started) { throw new UnsupportedOperationException(); }
        @Override public int countByType(int type) { throw new UnsupportedOperationException(); }
        @Override public List<Long> insert(List<Config> items) { throw new UnsupportedOperationException(); }
        @Override public void update(Config item) { throw new UnsupportedOperationException(); }
        @Override public void update(List<Config> items) { throw new UnsupportedOperationException(); }
    }

    @Test
    public void invalidationDuringDefaultSelectionKeepsTheReadSnapshot() throws Exception {
        Field field = Config.class.getDeclaredField("TYPE_CACHE");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<Integer, List<Config>> cache = (Map<Integer, List<Config>>) field.get(null);
        Config expected = new Config();
        for (int type = 0; type < 3; type++) {
            final int key = type;
            List<Config> items = new ArrayList<Config>() {
                @Override public boolean isEmpty() {
                    // Model cache invalidation after lookup, before the selected item is read.
                    cache.remove(key);
                    return super.isEmpty();
                }
            };
            items.add(expected);
            cache.put(type, items);
            try {
                assertSame(expected, Config.firstCached(type));
                assertNull(Config.firstCached(type));
            } finally {
                cache.remove(type);
            }
        }
    }
}
