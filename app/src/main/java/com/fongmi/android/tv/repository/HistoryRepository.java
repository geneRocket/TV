package com.fongmi.android.tv.repository;

import com.fongmi.android.tv.bean.History;
import com.fongmi.android.tv.bean.Vod;

import java.util.List;

/** Single data-access boundary for persisted playback history. */
public final class HistoryRepository {

    private static final HistoryRepository INSTANCE = new HistoryRepository();

    public static HistoryRepository get() {
        return INSTANCE;
    }

    private HistoryRepository() {
    }

    public History find(String key) {
        return History.find(key);
    }

    public List<History> all() {
        return History.get();
    }

    public List<History> all(int configId) {
        return History.get(configId);
    }

    public List<History> loaded() {
        return History.getLoaded();
    }

    public History prepare(History history, String key, int cid, Vod vod, float speed, String mark) {
        return History.prepare(history, key, cid, vod, speed, mark);
    }

    public History fromJson(String value) {
        return History.objectFrom(value);
    }

    public List<History> listFromJson(String value) {
        return History.arrayFrom(value);
    }

    public void delete(int cid) {
        History.delete(cid);
    }

    public void deleteLoaded() {
        History.deleteLoaded();
    }

    public void sync(List<History> targets) {
        History.sync(targets);
    }

    public void clearMemoryCache() {
        History.clearCache();
    }
}
