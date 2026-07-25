package com.fongmi.android.tv.api.loader;

import com.fongmi.android.tv.utils.ThreadPools;
import com.github.catvod.crawler.Spider;

import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;

/** Thread-safe cache and lifecycle owner shared by script-backed spider runtimes. */
final class SpiderStore {

    private final ConcurrentHashMap<String, Spider> spiders = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Object> locks = new ConcurrentHashMap<>();
    private volatile String recent;

    Spider getOrCreate(String key, Callable<Spider> factory) {
        Spider spider = spiders.get(key);
        if (spider != null) return spider;
        synchronized (locks.computeIfAbsent(key, ignored -> new Object())) {
            spider = spiders.get(key);
            if (spider != null) return spider;
            try {
                spider = factory.call();
            } catch (Exception error) {
                ThreadPools.log(error, "Spider initialization failed.");
                return null;
            }
            if (spider != null) spiders.put(key, spider);
            return spider;
        }
    }

    Spider get(String key) {
        return spiders.get(key);
    }

    Spider getRecent() {
        return recent == null ? null : get(recent);
    }

    void setRecent(String key) {
        recent = key;
    }

    void clear() {
        spiders.values().forEach(spider -> ThreadPools.loader().execute(spider::destroy));
        spiders.clear();
        locks.clear();
        recent = null;
    }

    void clear(String key) {
        Spider spider = spiders.remove(key);
        locks.remove(key);
        if (spider != null) ThreadPools.loader().execute(spider::destroy);
        if (key != null && key.equals(recent)) recent = null;
    }
}
