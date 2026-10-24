package com.fongmi.android.tv.repository;

import android.text.TextUtils;


import com.fongmi.android.tv.App;
import com.fongmi.android.tv.Setting;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.repository.ConfigRepository;
import com.fongmi.android.tv.db.AppDatabase;
import com.fongmi.android.tv.event.RefreshEvent;
import com.fongmi.android.tv.utils.ThreadPools;
import com.fongmi.android.tv.utils.ConfigUrlParser;
import com.github.catvod.utils.Trans;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;

import java.util.regex.Pattern;

import com.fongmi.android.tv.bean.*;
import com.fongmi.android.tv.utils.PersistedEntryKey;

/** Application-scoped persistence, cache and synchronization implementation for History. */
public final class HistoryStore {
    private HistoryStore() {}


    private static final Map<String, History> CACHE = new ConcurrentHashMap<>();
    private static final int MAX_CACHE_SIZE = 512;
    private static final Map<String, PendingWrite> PENDING_WRITES = new ConcurrentHashMap<>();
    private static final Set<String> ACTIVE_WRITES = ConcurrentHashMap.newKeySet();
    private static final Map<String, Long> WRITE_VERSIONS = new ConcurrentHashMap<>();
    private static final ExecutorService WRITE_EXECUTOR = ThreadPools.newSingle("history-write");
    private static final Object WRITE_LOCK = new Object();
    private static final Object QUEUE_LOCK = new Object();
    private static final long REFRESH_THROTTLE_MS = 1000;
    private static long lastRefreshAt;
    private static final Pattern PATTERN_END = Pattern.compile("(?i)[\\s\\p{P}\\p{S}]+$");
    private static final Pattern PATTERN_VERSION = Pattern.compile("(?i)(?:粤语版|国语版|普通话版|粤语中字|国语中字|国粤双语|双语版|中英双字|中文字幕|中字|粤语|国语|普通话|高清版|hd中字|hd|bd|正片|全集|4k|2160p|1080p|720p|x264|x265|h264|h265|mp4|mkv|m3u8|mp3|avi|flv|wmv|ts|mov)$");
    private static final Pattern PATTERN_ALL = Pattern.compile("[\\s\\p{P}\\p{S}]+");

    public static void clearCache() {
        CACHE.clear();
    }

    public static void cancelPendingWrites() {
        synchronized (WRITE_LOCK) {
            synchronized (QUEUE_LOCK) {
                for (String key : WRITE_VERSIONS.keySet()) WRITE_VERSIONS.merge(key, 1L, Long::sum);
                PENDING_WRITES.clear();
            }
        }
    }

    public static void withWritesPaused(Runnable action) {
        synchronized (WRITE_LOCK) {
            cancelPendingWrites();
            action.run();
        }
    }

    public static History create(String key, int cid, Vod vod, float speed) {
        History history = new History();
        history.setKey(key);
        history.setCid(cid);
        history.setVodName(vod.getVodName());
        findEpisode(history, vod.getVodFlags());
        history.setSpeed(speed);
        return history;
    }

    public static History prepare(History history, String key, int cid, Vod vod, float speed, String mark) {
        History prepared = history == null ? create(key, cid, vod, speed) : history.copy();
        prepared.setKey(key);
        prepared.setCid(cid);
        prepared.setVodName(vod.getVodName());
        if (!TextUtils.isEmpty(mark)) prepared.setVodRemarks(mark);
        findEpisode(prepared, vod.getVodFlags());
        prepared.setVodPic(vod.getVodPic());
        return prepared;
    }

    private static String rawSiteKey(String key) {
        return VodConfig.rawSiteKey(key);
    }

    private static boolean isEmpty(String value) {
        return value == null || value.length() == 0;
    }

    private static String cacheKey(int cid, String key) {
        return cid + "@" + key;
    }

    private static History cache(History item) {
        if (item == null) return null;
        String key = cacheKey(item.getCid(), item.getKey());
        History old = CACHE.get(key);
        if (old != null && old.getCreateTime() > item.getCreateTime()) return copy(old);
        if (old != null && old.getCreateTime() == item.getCreateTime() && old.getPosition() > 0 && item.getPosition() <= 0) return copy(old);
        CACHE.put(key, copy(item));
        trimCache();
        return item;
    }

    private static List<History> cache(List<History> items) {
        List<History> cached = new ArrayList<>(items.size());
        for (History item : items) cached.add(cache(item));
        return cached;
    }

    private static void removeCache(int cid, String key) {
        CACHE.remove(cacheKey(cid, key));
    }

    private static void trimCache() {
        while (CACHE.size() > MAX_CACHE_SIZE) {
            String eldest = CACHE.keySet().stream().findFirst().orElse(null);
            if (eldest == null) return;
            CACHE.remove(eldest);
        }
    }

    public static int keyCid(String key) {
        return PersistedEntryKey.config(key, VodConfig.getCid());
    }

    private static void removeCache(int cid) {
        for (String key : new ArrayList<>(CACHE.keySet())) if (key.startsWith(cid + "@")) CACHE.remove(key);
    }

    public static List<History> get() {
        return get(VodConfig.getCid());
    }

    public static List<History> getLoaded() {
        List<Integer> cids = getLoadedCids();
        if (cids.size() == 1) return get(cids.get(0));
        List<History> items = withCached(cids, cache(AppDatabase.get().getHistoryDao().find(cids)));
        return copy(unique(items, true));
    }

    public static List<History> get(int cid) {
        List<History> items = withCached(Collections.singletonList(cid), cache(AppDatabase.get().getHistoryDao().find(cid)));
        return copy(unique(items, false));
    }

    private static List<History> withCached(List<Integer> cids, List<History> items) {
        Map<String, History> merged = new LinkedHashMap<>();
        for (History item : items) merged.put(cacheKey(item.getCid(), item.getKey()), item);
        for (History item : CACHE.values()) {
            if (!cids.contains(item.getCid())) continue;
            String key = cacheKey(item.getCid(), item.getKey());
            History old = merged.get(key);
            if (isNewerOrBetterSameVersion(item, old)) merged.put(key, item);
        }
        List<History> result = new ArrayList<>(merged.values());
        result.sort((left, right) -> Long.compare(right.getCreateTime(), left.getCreateTime()));
        return result;
    }

    private static List<History> unique(List<History> items, boolean crossCid) {
        Map<String, History> unique = new LinkedHashMap<>();
        for (History item : items) {
            String key = uniqueKey(item, crossCid);
            History old = unique.get(key);
            if (isNewerOrBetterSameVersion(item, old)) unique.put(key, item);
        }
        return new ArrayList<>(unique.values());
    }

    public static boolean isNewerOrBetterSameVersion(History item, History old) {
        if (item == null) return false;
        if (old == null) return true;
        if (item.getCreateTime() != old.getCreateTime()) return item.getCreateTime() > old.getCreateTime();
        if (old.getPosition() <= 0 && item.getPosition() > 0) return true;
        return old.getDuration() <= 0 && item.getDuration() > 0;
    }

    private static String uniqueKey(History item, boolean crossCid) {
        String name = normalizedName(item.getVodName());
        String scope = crossCid ? "" : item.getCid() + "@";
        if (TextUtils.isEmpty(name)) return scope + item.getCid() + "@" + item.getKey();
        return scope + name;
    }

    private static String normalizedName(String name) {
        if (TextUtils.isEmpty(name)) return "";
        String value = Trans.s2t(name).trim().toLowerCase(Locale.US);
        String old;
        do {
            old = value;
            value = PATTERN_END.matcher(value).replaceAll("");
            value = PATTERN_VERSION.matcher(value).replaceAll("");
        } while (!old.equals(value));
        return PATTERN_ALL.matcher(value).replaceAll("");
    }

    private static List<Integer> getLoadedCids() {
        return getLoadedCids(getLoadedUrls(), VodConfig.getCid());
    }

    private static List<String> getLoadedUrls() {
        List<String> urls = VodConfig.get().getLoadUrls();
        if (urls.isEmpty()) {
            urls.addAll(ConfigUrlParser.parse(Setting.getVodConfigUrls()));
        }
        return urls;
    }

    private static List<Integer> getLoadedCids(List<String> urls, int fallbackCid) {
        Set<Integer> cids = new LinkedHashSet<>();
        Map<String, Integer> idMap = new HashMap<>();
        for (Config item : ConfigRepository.get().urls()) idMap.put(item.getUrl(), item.getId());
        for (String url : urls) {
            if (url == null || url.trim().isEmpty()) continue;
            Integer cid = idMap.get(url.trim());
            if (cid != null && cid > 0) cids.add(cid);
        }
        if (cids.isEmpty()) cids.add(fallbackCid);
        return new ArrayList<>(cids);
    }

    public static History find(String key) {
        int cid = keyCid(key);
        History item = CACHE.get(cacheKey(cid, key));
        if (item != null) return copy(item);
        History db = cache(AppDatabase.get().getHistoryDao().find(cid, key));
        return copy(db);
    }

    public static void delete(int cid) {
        synchronized (WRITE_LOCK) {
            cancelWrites(cid);
            AppDatabase.get().getHistoryDao().delete(cid);
            removeCache(cid);
        }
        RefreshEvent.history();
    }

    public static void deleteLoaded() {
        deleteLoaded(getLoadedUrls(), VodConfig.getCid());
    }

    public static Runnable prepareDeleteLoaded() {
        return prepareDeleteLoaded(HistoryStore::getLoadedUrls, VodConfig::getCid, HistoryStore::deleteLoaded);
    }

    public static Runnable prepareDeleteLoaded(java.util.function.Supplier<List<String>> urls,
                                        java.util.function.IntSupplier fallbackCid,
                                        java.util.function.BiConsumer<List<String>, Integer> delete) {
        List<String> selectedUrls = Collections.unmodifiableList(new ArrayList<>(urls.get()));
        int selectedCid = fallbackCid.getAsInt();
        return () -> delete.accept(selectedUrls, selectedCid);
    }

    private static void deleteLoaded(List<String> urls, int fallbackCid) {
        List<Integer> cids = getLoadedCids(urls, fallbackCid);
        if (cids.isEmpty()) return;
        if (cids.size() == 1) {
            delete(cids.get(0));
            return;
        }
        synchronized (WRITE_LOCK) {
            for (Integer cid : cids) cancelWrites(cid);
            AppDatabase.get().getHistoryDao().delete(cids);
            for (Integer cid : cids) removeCache(cid);
        }
        RefreshEvent.history();
    }

    private static void checkParam(History subject, History item) {
        if (subject.getOpening() == 0) subject.setOpening(item.getOpening());
        if (subject.getEnding() == 0) subject.setEnding(item.getEnding());
        if (subject.getSpeed() == 1) subject.setSpeed(item.getSpeed());
    }

    private static void checkProgress(History subject, History item) {
        if (item == null) return;
        if (item.getCreateTime() < subject.getCreateTime()) return;
        if (item.getCreateTime() == subject.getCreateTime() && subject.getPosition() > 0 && item.getPosition() <= 0) return;
        subject.setPosition(item.getPosition());
        if (item.getDuration() > 0) subject.setDuration(item.getDuration());
        if (item.getCreateTime() > subject.getCreateTime()) subject.setCreateTime(item.getCreateTime());
    }

    private static void merge(History subject, List<History> items, boolean force) {
        for (History item : items) {
            if (subject.getDuration() > 0 && item.getDuration() > 0) {
                long diff = Math.abs(subject.getDuration() - item.getDuration());
                long threshold = Math.min(subject.getDuration() / 10, 10 * 60 * 1000);
                if (diff > threshold) continue;
            }
            if (!force && subject.getKey().equals(item.getKey())) continue;
            checkParam(subject, item);
            checkProgress(subject, item);
            deleteSelf(item);
        }
    }

    public static void update(History subject) {
        synchronized (WRITE_LOCK) {
            merge(subject, find(subject), false);
            save(subject);
        }
    }

    public static History update(History subject, int cid) {
        return update(subject, cid, find(subject));
    }

    public static History update(History subject, int cid, List<History> items) {
        synchronized (WRITE_LOCK) {
            subject.setKey(PersistedEntryKey.rebase(subject.getKey(), cid));
            subject.setCid(cid);
            merge(subject, items, true);
            return save(subject);
        }
    }

    public static History save(History subject) {
        return save(subject, true);
    }

    private static History save(History subject, boolean cancelPending) {
        if (TextUtils.isEmpty(subject.getKey())) return subject;
        synchronized (WRITE_LOCK) {
            if (cancelPending) cancelWrite(subject.getCid(), subject.getKey());
            preserveCurrentProgressIfNeeded(subject);
            AppDatabase.get().getHistoryDao().insertOrUpdate(subject);
            return cache(subject);
        }
    }

    private static void preserveCurrentProgressIfNeeded(History subject) {
        if (subject.getPosition() > 0) return;
        History current = CACHE.get(cacheKey(subject.getCid(), subject.getKey()));
        if (current == null) current = AppDatabase.get().getHistoryDao().find(subject.getCid(), subject.getKey());
        if (current == null || current.getCreateTime() != subject.getCreateTime() || current.getPosition() <= 0) return;
        subject.setPosition(current.getPosition());
        if (subject.getDuration() <= 0 && current.getDuration() > 0) subject.setDuration(current.getDuration());
    }

    public static void saveAsync(History subject) {
        if (isEmpty(subject.getKey())) return;
        History snapshot = copy(subject);
        String key = cacheKey(snapshot.getCid(), snapshot.getKey());
        synchronized (QUEUE_LOCK) {
            cache(snapshot);
            long version = WRITE_VERSIONS.merge(key, 1L, Long::sum);
            PENDING_WRITES.put(key, new PendingWrite(snapshot, version));
            scheduleWrite(key);
        }
    }

    private static void scheduleWrite(String key) {
        if (!ACTIVE_WRITES.add(key)) return;
        WRITE_EXECUTOR.execute(() -> drainWrite(key));
    }

    private static void drainWrite(String key) {
        try {
            PendingWrite pending;
            while ((pending = PENDING_WRITES.remove(key)) != null) {
                synchronized (WRITE_LOCK) {
                    synchronized (QUEUE_LOCK) {
                        if (pending.version != WRITE_VERSIONS.getOrDefault(key, 0L)) continue;
                    }
                    // Never hold the enqueue lock during disk I/O: lifecycle saves
                    // must not wait for a slow database transaction on the UI thread.
                    preserveCurrentProgressIfNeeded(pending.item);
                    AppDatabase.get().getHistoryDao().insertOrUpdate(pending.item);
                    synchronized (QUEUE_LOCK) {
                        if (pending.version == WRITE_VERSIONS.getOrDefault(key, 0L)) cache(pending.item);
                    }
                    notifyHistoryChangedLocked();
                }
            }
        } finally {
            synchronized (QUEUE_LOCK) {
                ACTIVE_WRITES.remove(key);
                if (PENDING_WRITES.containsKey(key)) scheduleWrite(key);
                else WRITE_VERSIONS.remove(key);
            }
        }
    }

    private static void notifyHistoryChangedLocked() {
        long now = System.currentTimeMillis();
        if (now - lastRefreshAt < REFRESH_THROTTLE_MS) return;
        lastRefreshAt = now;
        RefreshEvent.history();
    }

    public static History delete(History subject) {
        for (History item : find(subject)) deleteSelf(item, false);
        return deleteSelf(subject, true);
    }

    private static History deleteSelf(History subject) {
        return deleteSelf(subject, true);
    }

    private static History deleteSelf(History subject, boolean notify) {
        synchronized (WRITE_LOCK) {
            cancelWrite(subject.getCid(), subject.getKey());
            AppDatabase.get().getHistoryDao().delete(subject.getCid(), subject.getKey());
            AppDatabase.get().getTrackDao().delete(subject.getKey());
            removeCache(subject.getCid(), subject.getKey());
        }
        if (notify) RefreshEvent.history();
        return subject;
    }

    private static void cancelWrites(int cid) {
        String prefix = cid + "@";
        for (String key : WRITE_VERSIONS.keySet()) if (key.startsWith(prefix)) cancelWrite(key);
    }

    private static void cancelWrite(int cid, String key) {
        cancelWrite(cacheKey(cid, key));
    }

    private static void cancelWrite(String key) {
        synchronized (QUEUE_LOCK) {
            WRITE_VERSIONS.merge(key, 1L, Long::sum);
            PENDING_WRITES.remove(key);
            if (!ACTIVE_WRITES.contains(key)) WRITE_VERSIONS.remove(key);
        }
    }

    public static List<History> find(History subject) {
        return find(subject, getLoadedCids());
    }

    private static List<History> find(History subject, List<Integer> cids) {
        String name = normalizedName(subject.getVodName());
        List<History> items = TextUtils.isEmpty(name) ? AppDatabase.get().getHistoryDao().findByName(cids, subject.getVodName()) : findByNormalizedName(subject, name, cids);
        Map<String, History> unique = new LinkedHashMap<>();
        for (History item : cache(items)) {
            History old = unique.get(item.getKey());
            if (subject.isNewerOrBetterSameVersion(item, old)) unique.put(item.getKey(), item);
        }
        return copy(new ArrayList<>(unique.values()));
    }

    public static List<History> find(History subject, int cid) {
        return find(subject, Collections.singletonList(cid));
    }

    public static History findBestMatch(History subject) {
        History best = null;
        for (History item : find(subject)) {
            if (item.getCid() != subject.getCid()) continue;
            if (!Objects.equals(rawSiteKey(item.getSiteKey()), rawSiteKey(subject.getSiteKey()))) continue;
            if (best == null || subject.isNewerOrBetterSameVersion(item, best)) best = item;
        }
        return copy(best);
    }

    private static List<History> findByNormalizedName(History subject, String name, List<Integer> cids) {
        List<History> result = new ArrayList<>();
        for (History item : withCached(cids, cache(AppDatabase.get().getHistoryDao().find(cids)))) {
            if (name.equals(normalizedName(item.getVodName()))) result.add(item);
        }
        return result;
    }

    public static void findEpisode(History subject, List<Flag> flags) {
        if (flags.size() > 0 && TextUtils.isEmpty(subject.getVodFlag())) {
            subject.setVodFlag(flags.get(0).getFlag());
        }
        if (flags.size() > 0 && TextUtils.isEmpty(subject.getVodRemarks()) && flags.get(0).getEpisodes().size() > 0) {
            subject.setVodRemarks(flags.get(0).getEpisodes().get(0).getName());
        }
        EpisodeMatch best = null;
        for (History item : find(subject)) {
            EpisodeMatch match = findEpisode(subject, flags, item.getVodFlag(), item.getVodRemarks());
            if (match == null) continue;
            if (isBetterMatch(subject, best, match, item)) best = match.history(item);
        }
        if (best == null) return;
        subject.setVodFlag(best.flag.getFlag());
        subject.setVodRemarks(best.episode.getName());
        subject.setEpisodeUrl(best.episode.getUrl());
        checkParam(subject, best.history);
        checkProgress(subject, best.history);
    }

    private static boolean isBetterMatch(History subject, EpisodeMatch best, EpisodeMatch match, History item) {
        if (best == null) return true;
        if (match.sameFlag != best.sameFlag) return match.sameFlag;
        if (best.history.getPosition() <= 0 && item.getPosition() > 0) return true;
        return item.getCreateTime() > best.history.getCreateTime();
    }

    private static EpisodeMatch findEpisode(History subject, List<Flag> flags, String targetFlag, String remarks) {
        if (TextUtils.isEmpty(remarks)) return null;
        for (Flag flag : flags) {
            if (!flag.getFlag().equals(targetFlag)) continue;
            Episode episode = flag.find(remarks, true);
            if (episode != null) return new EpisodeMatch(flag, episode, true);
        }
        for (Flag flag : flags) {
            Episode episode = flag.find(remarks, true);
            if (episode != null) return new EpisodeMatch(flag, episode, false);
        }
        return null;
    }

    public static void startSync(int cid, List<History> targets) {
        startSync(cid, targets, new SyncAccess() {
            @Override public List<History> find(History target, int config) { return HistoryStore.find(target, config); }
            @Override public void update(History target, int config, List<History> items) { HistoryStore.update(target, config, items); }
        });
    }

    public interface SyncAccess {
        List<History> find(History target, int config);
        void update(History target, int config, List<History> items);
    }

    public static void startSync(int cid, List<History> targets, SyncAccess access) {
        for (History target : targets) {
            List<History> items = access.find(target, cid);
            if (items.isEmpty()) {
                access.update(target, cid, items);
                continue;
            }
            for (History item : items) {
                if (target.getCreateTime() > item.getCreateTime()) {
                    access.update(target, cid, items);
                    break;
                }
            }
        }
    }

    public static void sync(List<History> targets) {
        sync(VodConfig.getCid(), targets, false);
    }

    public static void sync(int cid, List<History> targets, boolean force) {
        App.execute(() -> {
            synchronized (WRITE_LOCK) {
                if (force) delete(cid);
                startSync(cid, targets);
            }
            RefreshEvent.history();
        });
    }

    private static History copy(History item) { return item == null ? null : item.copy(); }

    private static List<History> copy(List<History> items) {
        List<History> result = new ArrayList<>(items.size());
        for (History item : items) result.add(copy(item));
        return result;
    }

    private static class EpisodeMatch {

        private final Flag flag;
        private final Episode episode;
        private final boolean sameFlag;
        private History history;

        private EpisodeMatch(Flag flag, Episode episode, boolean sameFlag) {
            this.flag = flag;
            this.episode = episode;
            this.sameFlag = sameFlag;
        }

        private EpisodeMatch history(History history) {
            this.history = history;
            return this;
        }
    }

    private static class PendingWrite {

        private final History item;
        private final long version;

        private PendingWrite(History item, long version) {
            this.item = item;
            this.version = version;
        }
    }
}
