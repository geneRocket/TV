package com.fongmi.android.tv.player.exo;

import androidx.media3.database.StandaloneDatabaseProvider;
import androidx.media3.datasource.cache.Cache;
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor;
import androidx.media3.datasource.cache.NoOpCacheEvictor;
import androidx.media3.datasource.cache.SimpleCache;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.utils.ThreadPools;
import com.github.catvod.utils.Path;

public class CacheManager {

    private static final long MAX_CACHE_BYTES = 128L * 1024 * 1024;
    private SimpleCache cache;
    private StandaloneDatabaseProvider databaseProvider;

    private static class Loader {
        static volatile CacheManager INSTANCE = new CacheManager();
    }

    public static CacheManager get() {
        return Loader.INSTANCE;
    }

    public synchronized Cache getCache() {
        if (cache == null) create();
        return cache;
    }

    private synchronized void create() {
        if (cache != null) return;
        StandaloneDatabaseProvider provider = new StandaloneDatabaseProvider(App.get());
        try {
            cache = new SimpleCache(Path.exo(), new LeastRecentlyUsedCacheEvictor(MAX_CACHE_BYTES), provider);
            databaseProvider = provider;
        } catch (RuntimeException e) {
            provider.close();
            throw e;
        }
    }

    /** Releases cache file handles and its database provider; cached media remains available. */
    public synchronized void release() {
        if (cache == null) return;
        try {
            cache.release();
        } catch (RuntimeException e) {
            // Teardown must not crash the activity if a device-specific cache/SQLite close
            // fails. The provider is still closed below and the next playback recreates both.
            ThreadPools.log(e, "Exo cache release failed.");
        } finally {
            cache = null;
            if (databaseProvider != null) {
                try {
                    databaseProvider.close();
                } catch (RuntimeException e) {
                    ThreadPools.log(e, "Exo cache database release failed.");
                }
                databaseProvider = null;
            }
        }
    }
}
