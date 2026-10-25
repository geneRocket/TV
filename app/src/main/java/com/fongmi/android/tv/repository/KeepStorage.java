package com.fongmi.android.tv.repository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.fongmi.android.tv.bean.*;
import com.fongmi.android.tv.utils.PersistedEntryKey;

/** Application-scoped persistence, cache and synchronization implementation for Keep. */
public final class KeepStorage {
    private final java.util.function.Supplier<com.fongmi.android.tv.db.dao.KeepDao> dao;
    private final Runnable changed;
    private final java.util.concurrent.Executor executor;
    public KeepStorage(java.util.function.Supplier<com.fongmi.android.tv.db.dao.KeepDao> dao, Runnable changed, java.util.concurrent.Executor executor) {
        this.dao = dao; this.changed = changed; this.executor = executor;
    }

    private final Map<String, Keep> VOD_CACHE = new ConcurrentHashMap<>();
    private final Map<String, Keep> LIVE_CACHE = new ConcurrentHashMap<>();
    private final Set<String> VOD_MISSES = ConcurrentHashMap.newKeySet();
    private final Set<String> LIVE_MISSES = ConcurrentHashMap.newKeySet();
    private static final int MAX_CACHE_SIZE = 256;

    public void clearCache() {
        VOD_CACHE.clear();
        LIVE_CACHE.clear();
        VOD_MISSES.clear();
        LIVE_MISSES.clear();
    }

    private String vodCacheKey(int cid, String key) {
        return cid + "@" + key;
    }

    public int keyCid(String key, int fallback) {
        return PersistedEntryKey.config(key, fallback);
    }

    private Keep cache(Keep item) {
        if (item == null) return null;
        Keep snapshot = copy(item);
        if (item.getType() == 1) {
            LIVE_CACHE.put(item.getKey(), snapshot);
            LIVE_MISSES.remove(item.getKey());
            trimCache(LIVE_CACHE);
        } else {
            String key = vodCacheKey(item.getCid(), item.getKey());
            VOD_CACHE.put(key, snapshot);
            VOD_MISSES.remove(key);
            trimCache(VOD_CACHE);
        }
        return item;
    }

    private void trimCache(Map<String, Keep> cache) {
        while (cache.size() > MAX_CACHE_SIZE) {
            String eldest = cache.keySet().stream().findFirst().orElse(null);
            if (eldest == null) return;
            cache.remove(eldest);
        }
    }

    private void cacheMiss(Set<String> misses, String key) {
        misses.add(key);
        while (misses.size() > MAX_CACHE_SIZE) {
            String item = misses.stream().findFirst().orElse(null);
            if (item == null) return;
            misses.remove(item);
        }
    }

    public Keep find(String key, int fallback) {
        int cid = keyCid(key, fallback);
        String cacheKey = vodCacheKey(cid, key);
        Keep item = VOD_CACHE.get(cacheKey);
        if (item != null) return copy(item);
        if (VOD_MISSES.contains(cacheKey)) return null;
        item = dao.get().find(cid, key);
        if (item == null) cacheMiss(VOD_MISSES, cacheKey);
        return copy(cache(item));
    }

    public Keep find(int cid, String key) {
        String cacheKey = vodCacheKey(cid, key);
        Keep item = VOD_CACHE.get(cacheKey);
        if (item != null) return copy(item);
        if (VOD_MISSES.contains(cacheKey)) return null;
        item = dao.get().find(cid, key);
        if (item == null) cacheMiss(VOD_MISSES, cacheKey);
        return copy(cache(item));
    }

    public boolean exist(String key) {
        Keep item = LIVE_CACHE.get(key);
        if (item != null) return true;
        if (LIVE_MISSES.contains(key)) return false;
        item = dao.get().find(key);
        if (item == null) cacheMiss(LIVE_MISSES, key);
        return cache(item) != null;
    }

    public void deleteAll() {
        dao.get().delete();
        clearCache();
        changed.run();
    }

    public void delete(int cid) {
        dao.get().delete(cid);
        for (String key : new ArrayList<>(VOD_CACHE.keySet())) if (key.startsWith(cid + "@")) VOD_CACHE.remove(key);
        changed.run();
    }

    public void deleteLoaded(PersistenceScope scope) {
        for (Integer cid : scope.ids()) {
            dao.get().delete(cid);
            for (String key : new ArrayList<>(VOD_CACHE.keySet())) if (key.startsWith(cid + "@")) VOD_CACHE.remove(key);
        }
        changed.run();
    }

    public void delete(String key) {
        dao.get().delete(key);
        LIVE_CACHE.remove(key);
        cacheMiss(LIVE_MISSES, key);
        changed.run();
    }

    public List<Keep> getVod() {
        List<Keep> items = dao.get().getVod();
        for (Keep item : items) cache(item);
        return copy(items);
    }

    public List<Keep> getLive() {
        List<Keep> items = dao.get().getLive();
        for (Keep item : items) cache(item);
        return copy(items);
    }

    public void save(Keep subject, int cid) {
        if (subject.getType() == 0) subject.setKey(PersistedEntryKey.rebase(subject.getKey(), cid));
        subject.setCid(cid);
        dao.get().insertOrUpdate(subject);
        cache(subject);
    }

    public void save(Keep subject) {
        dao.get().insert(subject);
        cache(subject);
    }

    public Keep delete(Keep subject) {
        dao.get().delete(subject.getCid(), subject.getKey());
        if (subject.getType() == 1) {
            LIVE_CACHE.remove(subject.getKey());
            cacheMiss(LIVE_MISSES, subject.getKey());
        } else {
            String key = vodCacheKey(subject.getCid(), subject.getKey());
            VOD_CACHE.remove(key);
            cacheMiss(VOD_MISSES, key);
        }
        changed.run();
        return subject;
    }

    public void startSync(Map<Integer,Integer> ids, List<Keep> targets) {
        for (Keep target : targets) {
            Integer id = ids.get(target.getCid());
            if (id != null) save(target, id);
        }
    }
    public void sync(Map<Integer,Integer> ids, List<Keep> targets, boolean force) {
        Map<Integer,Integer> capturedIds = java.util.Collections.unmodifiableMap(new HashMap<>(ids));
        List<Keep> captured = copy(targets);
        executor.execute(() -> {
            if (force) deleteAll();
            startSync(capturedIds, captured);
            changed.run();
        });
    }

    private Keep copy(Keep item) { return item == null ? null : item.copy(); }

    private List<Keep> copy(List<Keep> items) {
        List<Keep> result = new ArrayList<>(items.size());
        for (Keep item : items) result.add(copy(item));
        return result;
    }
}
