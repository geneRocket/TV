package com.fongmi.android.tv.repository;

import com.fongmi.android.tv.utils.ConfigUrlParser;

import com.fongmi.android.tv.Setting;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.repository.ConfigRepository;
import com.fongmi.android.tv.db.AppDatabase;
import com.fongmi.android.tv.event.RefreshEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.fongmi.android.tv.bean.*;
import com.fongmi.android.tv.utils.PersistedEntryKey;

/** Application-scoped persistence, cache and synchronization implementation for Keep. */
public final class KeepStore {
    private KeepStore() {}


    private static final Map<String, Keep> VOD_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, Keep> LIVE_CACHE = new ConcurrentHashMap<>();
    private static final Set<String> VOD_MISSES = ConcurrentHashMap.newKeySet();
    private static final Set<String> LIVE_MISSES = ConcurrentHashMap.newKeySet();
    private static final int MAX_CACHE_SIZE = 256;

    public static void clearCache() {
        VOD_CACHE.clear();
        LIVE_CACHE.clear();
        VOD_MISSES.clear();
        LIVE_MISSES.clear();
    }

    private static String vodCacheKey(int cid, String key) {
        return cid + "@" + key;
    }

    public static int keyCid(String key) {
        return PersistedEntryKey.config(key, VodConfig.getCid());
    }

    private static Keep cache(Keep item) {
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

    private static void trimCache(Map<String, Keep> cache) {
        while (cache.size() > MAX_CACHE_SIZE) {
            String eldest = cache.keySet().stream().findFirst().orElse(null);
            if (eldest == null) return;
            cache.remove(eldest);
        }
    }

    private static void cacheMiss(Set<String> misses, String key) {
        misses.add(key);
        while (misses.size() > MAX_CACHE_SIZE) {
            String item = misses.stream().findFirst().orElse(null);
            if (item == null) return;
            misses.remove(item);
        }
    }

    public static Keep find(String key) {
        int cid = keyCid(key);
        String cacheKey = vodCacheKey(cid, key);
        Keep item = VOD_CACHE.get(cacheKey);
        if (item != null) return copy(item);
        if (VOD_MISSES.contains(cacheKey)) return null;
        item = AppDatabase.get().getKeepDao().find(cid, key);
        if (item == null) cacheMiss(VOD_MISSES, cacheKey);
        return copy(cache(item));
    }

    public static Keep find(int cid, String key) {
        String cacheKey = vodCacheKey(cid, key);
        Keep item = VOD_CACHE.get(cacheKey);
        if (item != null) return copy(item);
        if (VOD_MISSES.contains(cacheKey)) return null;
        item = AppDatabase.get().getKeepDao().find(cid, key);
        if (item == null) cacheMiss(VOD_MISSES, cacheKey);
        return copy(cache(item));
    }

    public static boolean exist(String key) {
        Keep item = LIVE_CACHE.get(key);
        if (item != null) return true;
        if (LIVE_MISSES.contains(key)) return false;
        item = AppDatabase.get().getKeepDao().find(key);
        if (item == null) cacheMiss(LIVE_MISSES, key);
        return cache(item) != null;
    }

    public static void deleteAll() {
        AppDatabase.get().getKeepDao().delete();
        clearCache();
        RefreshEvent.keep();
    }

    public static void delete(int cid) {
        AppDatabase.get().getKeepDao().delete(cid);
        for (String key : new ArrayList<>(VOD_CACHE.keySet())) if (key.startsWith(cid + "@")) VOD_CACHE.remove(key);
        RefreshEvent.keep();
    }

    public static void deleteLoaded() {
        Map<String, Integer> idMap = new HashMap<>();
        Set<Integer> cids = new LinkedHashSet<>();
        for (Config item : ConfigRepository.get().urls()) idMap.put(item.getUrl(), item.getId());
        List<String> urls = VodConfig.get().getLoadUrls();
        if (urls.isEmpty()) {
            urls.addAll(ConfigUrlParser.parse(Setting.getVodConfigUrls()));
        }
        for (String url : urls) {
            if (url == null || url.trim().isEmpty()) continue;
            Integer cid = idMap.get(url.trim());
            if (cid != null && cid > 0) cids.add(cid);
        }
        if (cids.isEmpty()) {
            if (VodConfig.getCid() > 0) delete(VodConfig.getCid());
            return;
        }
        for (Integer cid : cids) {
            AppDatabase.get().getKeepDao().delete(cid);
            for (String key : new ArrayList<>(VOD_CACHE.keySet())) if (key.startsWith(cid + "@")) VOD_CACHE.remove(key);
        }
        RefreshEvent.keep();
    }

    public static void delete(String key) {
        AppDatabase.get().getKeepDao().delete(key);
        LIVE_CACHE.remove(key);
        cacheMiss(LIVE_MISSES, key);
        RefreshEvent.keep();
    }

    public static List<Keep> getVod() {
        List<Keep> items = AppDatabase.get().getKeepDao().getVod();
        for (Keep item : items) cache(item);
        return copy(items);
    }

    public static List<Keep> getLive() {
        List<Keep> items = AppDatabase.get().getKeepDao().getLive();
        for (Keep item : items) cache(item);
        return copy(items);
    }

    public static void save(Keep subject, int cid) {
        if (subject.getType() == 0) subject.setKey(PersistedEntryKey.rebase(subject.getKey(), cid));
        subject.setCid(cid);
        AppDatabase.get().getKeepDao().insertOrUpdate(subject);
        cache(subject);
    }

    public static void save(Keep subject) {
        AppDatabase.get().getKeepDao().insert(subject);
        cache(subject);
    }

    public static Keep delete(Keep subject) {
        AppDatabase.get().getKeepDao().delete(subject.getCid(), subject.getKey());
        if (subject.getType() == 1) {
            LIVE_CACHE.remove(subject.getKey());
            cacheMiss(LIVE_MISSES, subject.getKey());
        } else {
            String key = vodCacheKey(subject.getCid(), subject.getKey());
            VOD_CACHE.remove(key);
            cacheMiss(VOD_MISSES, key);
        }
        RefreshEvent.keep();
        return subject;
    }

    public static void startSync(List<Config> configs, List<Keep> targets) {
        for (Keep target : targets) {
            for (Config config : configs) {
                if (target.getCid() == config.getId()) {
                    save(target, ConfigRepository.get().find(config).getId());
                    break;
                }
            }
        }
    }

    public static void sync(List<Config> configs, List<Keep> targets) {
        sync(configs, targets, false);
    }

    public static void sync(List<Config> configs, List<Keep> targets, boolean force) {
        App.execute(() -> {
            if (force) deleteAll();
            startSync(configs, targets);
            RefreshEvent.keep();
        });
    }

    private static Keep copy(Keep item) { return item == null ? null : item.copy(); }

    private static List<Keep> copy(List<Keep> items) {
        List<Keep> result = new ArrayList<>(items.size());
        for (Keep item : items) result.add(copy(item));
        return result;
    }
}
