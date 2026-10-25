package com.fongmi.android.tv.repository;

import com.fongmi.android.tv.bean.History;
import com.fongmi.android.tv.bean.Vod;

import java.util.List;

/** Single data-access boundary for persisted playback history. */
public final class HistoryRepository {

    private static final class Default { static final HistoryRepository INSTANCE =
            new HistoryRepository(PersistenceServices.history(), PersistenceServices::captureScope); }

    public static HistoryRepository get() {
        return Default.INSTANCE;
    }

    private final HistoryStorage storage;
    private final java.util.function.Supplier<PersistenceScope> scopes;
    public HistoryRepository(HistoryStorage storage, java.util.function.Supplier<PersistenceScope> scopes) {
        this.storage = java.util.Objects.requireNonNull(storage); this.scopes = java.util.Objects.requireNonNull(scopes);
    }
    public HistoryRepository in(PersistenceScope scope) { return new HistoryRepository(storage, () -> scope); }
    public PersistenceScope captureScope() { return scopes.get(); }

    public History find(String key) {
        return storage.find(key, scopes.get().currentId());
    }

    public List<History> all() {
        return storage.get(scopes.get().currentId());
    }

    public List<History> all(int configId) {
        return storage.get(configId);
    }

    public List<History> loaded() {
        return storage.getLoaded(scopes.get());
    }

    public History prepare(History history, String key, int cid, Vod vod, float speed, String mark) {
        return storage.prepare(history, key, cid, vod, speed, mark, scopes.get());
    }

    public History prepareDefault(String key, int cid, Vod vod, float speed, String mark) {
        History history = new History();
        history.setKey(key);
        history.setCid(cid);
        history.setVodName(vod.getVodName());
        history.setVodPic(vod.getVodPic());
        history.setSpeed(speed);
        if (!vod.getVodFlags().isEmpty()) {
            com.fongmi.android.tv.bean.Flag flag = vod.getVodFlags().get(0);
            history.setVodFlag(flag.getFlag());
            if (!flag.getEpisodes().isEmpty()) history.setVodRemarks(flag.getEpisodes().get(0).getName());
        }
        if (mark != null && !mark.isEmpty()) history.setVodRemarks(mark);
        return history;
    }

    public History prepareBestMatch(String key, int cid, Vod vod, float speed, String mark) {
        PersistenceScope scope = scopes.get();
        History prepared = storage.prepare(storage.find(key, scope.currentId()), key, cid, vod, speed, mark, scope);
        History matched = storage.findBestMatch(prepared, scope);
        return History.isNewerOrBetterSameVersion(matched, prepared)
                ? storage.prepare(matched, key, cid, vod, speed, mark, scope)
                : prepared;
    }

    public History save(History history) {
        return storage.save(history);
    }

    public void saveAsync(History history) { storage.saveAsync(history); }

    public History delete(History history) {
        return storage.delete(history, scopes.get());
    }

    public History fromJson(String value) {
        return History.objectFrom(value);
    }

    public List<History> listFromJson(String value) {
        return History.arrayFrom(value);
    }

    public void delete(int cid) {
        storage.delete(cid);
    }

    public void deleteLoaded() {
        storage.deleteLoaded(scopes.get());
    }

    public Runnable prepareDeleteLoaded() {
        PersistenceScope captured = scopes.get();
        return () -> storage.deleteLoaded(captured);
    }

    public void sync(List<History> targets) {
        storage.sync(scopes.get().currentId(), targets, false);
    }

    public void sync(int cid, List<History> targets, boolean force) {
        storage.sync(cid, targets, force);
    }

    public void clearMemoryCache() {
        storage.clearCache();
    }

    public void cancelPendingWrites() {
        storage.cancelPendingWrites();
    }

    public void withWritesPaused(Runnable action) {
        storage.withWritesPaused(action);
    }
}
