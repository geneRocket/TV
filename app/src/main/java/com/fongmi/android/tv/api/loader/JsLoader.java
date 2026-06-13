package com.fongmi.android.tv.api.loader;

import com.fongmi.android.tv.App;
import com.fongmi.quickjs.crawler.Loader;
import com.github.catvod.crawler.Spider;
import com.github.catvod.crawler.SpiderNull;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class JsLoader {

    private final ConcurrentHashMap<String, Spider> spiders;
    private final ConcurrentHashMap<String, Object> locks;
    private final Loader loader;
    private String recent;

    public JsLoader() {
        spiders = new ConcurrentHashMap<>();
        locks = new ConcurrentHashMap<>();
        loader = new Loader();
    }

    public void clear() {
        spiders.values().forEach(spider -> App.execute(spider::destroy));
        spiders.clear();
        locks.clear();
        recent = null;
    }

    public void clear(String key) {
        Spider spider = spiders.remove(key);
        locks.remove(key);
        if (spider != null) App.execute(spider::destroy);
        if (key != null && key.equals(recent)) recent = null;
    }

    public void setRecent(String recent) {
        this.recent = recent;
    }

    public Spider getSpider(String key, String api, String ext) {
        Spider spider = spiders.get(key);
        if (spider != null) return spider;
        synchronized (locks.computeIfAbsent(key, k -> new Object())) {
            spider = spiders.get(key);
            if (spider != null) return spider;
            spider = createSpider(key, api, ext);
            spiders.put(key, spider);
            return spider;
        }
    }

    public Spider getCached(String key) {
        return spiders.get(key);
    }

    private Spider createSpider(String key, String api, String ext) {
        try {
            Spider spider = loader.spider(key, api);
            spider.init(App.get(), ext);
            return spider;
        } catch (Throwable e) {
            e.printStackTrace();
            return new SpiderNull();
        }
    }

    public Object[] proxyInvoke(Map<String, String> params) {
        try {
            if (!params.containsKey("siteKey")) return recent == null ? null : getRecent(recent).proxy(params);
            return BaseLoader.get().getSpider(params).proxy(params);
        } catch (Throwable e) {
            e.printStackTrace();
            return null;
        }
    }

    private Spider getRecent(String key) {
        Spider spider = getCached(key);
        return spider == null ? new SpiderNull() : spider;
    }
}
