package com.fongmi.android.tv.repository;

import com.fongmi.android.tv.bean.Config;
import com.fongmi.android.tv.bean.Keep;

import java.util.List;

/** Single data-access boundary for persisted VOD and live favorites. */
public final class KeepRepository {

    private static final KeepRepository INSTANCE = new KeepRepository();

    public static KeepRepository get() {
        return INSTANCE;
    }

    private KeepRepository() {
    }

    public Keep find(String key) {
        return KeepStore.find(key);
    }

    public List<Keep> vod() {
        return KeepStore.getVod();
    }

    public List<Keep> live() {
        return KeepStore.getLive();
    }

    public List<Keep> listFromJson(String value) {
        return Keep.arrayFrom(value);
    }

    public void save(Keep keep) {
        KeepStore.save(keep);
    }

    public Keep delete(Keep keep) {
        return KeepStore.delete(keep);
    }

    public void deleteAll() {
        KeepStore.deleteAll();
    }

    public void delete(int cid) {
        KeepStore.delete(cid);
    }

    public void delete(String key) {
        KeepStore.delete(key);
    }

    public void sync(List<Config> configs, List<Keep> targets) {
        KeepStore.sync(configs, targets);
    }

    public void sync(List<Config> configs, List<Keep> targets, boolean force) {
        KeepStore.sync(configs, targets, force);
    }

    public void clearMemoryCache() {
        KeepStore.clearCache();
    }
}
