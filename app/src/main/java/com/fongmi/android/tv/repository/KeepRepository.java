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
        return Keep.find(key);
    }

    public List<Keep> vod() {
        return Keep.getVod();
    }

    public List<Keep> live() {
        return Keep.getLive();
    }

    public List<Keep> listFromJson(String value) {
        return Keep.arrayFrom(value);
    }

    public void deleteAll() {
        Keep.deleteAll();
    }

    public void delete(int cid) {
        Keep.delete(cid);
    }

    public void delete(String key) {
        Keep.delete(key);
    }

    public void sync(List<Config> configs, List<Keep> targets) {
        Keep.sync(configs, targets);
    }

    public void clearMemoryCache() {
        Keep.clearCache();
    }
}
