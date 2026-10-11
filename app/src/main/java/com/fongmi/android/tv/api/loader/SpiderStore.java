package com.fongmi.android.tv.api.loader;

import com.fongmi.android.tv.utils.ThreadPools;
import com.github.catvod.crawler.Spider;

import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/** Thread-safe cache and lifecycle owner shared by script-backed spider runtimes. */
final class SpiderStore {

    private final ConcurrentHashMap<String, Spider> spiders = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Object> locks = new ConcurrentHashMap<>();
    private volatile String recent;
    private final Consumer<Spider> destroy;

    SpiderStore() {
        this(spider -> ThreadPools.loader().execute(spider::destroy));
    }

    SpiderStore(Consumer<Spider> destroy) {
        this.destroy = destroy;
    }

    Spider getOrCreate(String key, Callable<Spider> factory) {
        Object lock;
        Spider spider;
        synchronized (this) {
            spider = spiders.get(key);
            if (spider != null) return spider;
            lock = locks.computeIfAbsent(key, ignored -> new Object());
        }
        synchronized (lock) {
            synchronized (this) {
                if (locks.get(key) != lock) return null;
                spider = spiders.get(key);
                if (spider != null) return spider;
            }
            try {
                spider = factory.call();
            } catch (Exception error) {
                ThreadPools.log(error, "Spider initialization failed.");
                return null;
            }
            synchronized (this) {
                if (locks.get(key) != lock) {
                    if (spider != null) destroy.accept(spider);
                    return null;
                }
                if (spider != null) spiders.put(key, spider);
            }
            return spider;
        }
    }

    Spider get(String key) {
        return spiders.get(key);
    }

    Spider getRecent() {
        return recent == null ? null : get(recent);
    }

    synchronized void setRecent(String key) {
        recent = key;
    }

    synchronized void clear() {
        spiders.values().forEach(destroy);
        spiders.clear();
        locks.clear();
        recent = null;
    }

    synchronized void clear(String key) {
        Spider spider = spiders.remove(key);
        locks.remove(key);
        if (spider != null) destroy.accept(spider);
        if (key != null && key.equals(recent)) recent = null;
    }
}
