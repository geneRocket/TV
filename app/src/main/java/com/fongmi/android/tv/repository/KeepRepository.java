package com.fongmi.android.tv.repository;

import com.fongmi.android.tv.bean.Config;
import com.fongmi.android.tv.bean.Keep;

import java.util.List;

/** Single data-access boundary for persisted VOD and live favorites. */
public final class KeepRepository {

    private static final class Default { static final KeepRepository INSTANCE =
            new KeepRepository(PersistenceServices.keep(), PersistenceServices::captureScope, PersistenceServices::importIds); }

    public static KeepRepository get() {
        return Default.INSTANCE;
    }

    private final KeepStorage storage;
    private final java.util.function.Supplier<PersistenceScope> scopes;
    private final java.util.function.Function<List<Config>, java.util.Map<Integer,Integer>> imports;
    public KeepRepository(KeepStorage storage, java.util.function.Supplier<PersistenceScope> scopes,
                          java.util.function.Function<List<Config>, java.util.Map<Integer,Integer>> imports) {
        this.storage = java.util.Objects.requireNonNull(storage); this.scopes = java.util.Objects.requireNonNull(scopes);
        this.imports = java.util.Objects.requireNonNull(imports);
    }
    public KeepRepository in(PersistenceScope scope) { return new KeepRepository(storage, () -> scope, imports); }

    public Keep find(String key) {
        return storage.find(key,scopes.get().currentId());
    }

    public List<Keep> vod() {
        return storage.getVod();
    }

    public List<Keep> live() {
        return storage.getLive();
    }

    public List<Keep> listFromJson(String value) {
        return Keep.arrayFrom(value);
    }

    public void save(Keep keep) {
        storage.save(keep);
    }

    public Keep delete(Keep keep) {
        return storage.delete(keep);
    }

    public void deleteAll() {
        storage.deleteAll();
    }

    public void delete(int cid) {
        storage.delete(cid);
    }

    public void delete(String key) {
        storage.delete(key);
    }

    public void sync(List<Config> configs, List<Keep> targets) {
        storage.sync(imports.apply(configs), targets, false);
    }

    public void sync(List<Config> configs, List<Keep> targets, boolean force) {
        storage.sync(imports.apply(configs), targets, force);
    }

    public void clearMemoryCache() {
        storage.clearCache();
    }
}
