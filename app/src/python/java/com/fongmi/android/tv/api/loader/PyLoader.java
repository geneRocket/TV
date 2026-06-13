package com.fongmi.android.tv.api.loader;

import com.fongmi.android.tv.App;
import com.fongmi.chaquo.Loader;
import com.github.catvod.crawler.Spider;
import com.github.catvod.crawler.SpiderNull;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class PyLoader {

    private final ConcurrentHashMap<String, Spider> spiders;
    private final Loader loader;
    private String recent;

    public PyLoader() {
        spiders = new ConcurrentHashMap<>();
        loader = new Loader();
    }

    public void clear() {
        spiders.values().forEach(spider -> App.execute(spider::destroy));
        spiders.clear();
    }

    public void clear(String key) {
        Spider spider = spiders.remove(key);
        if (spider != null) App.execute(spider::destroy);
        if (key != null && key.equals(recent)) recent = null;
    }

    public void setRecent(String recent) {
        this.recent = recent;
    }

    public Spider getSpider(String key, String api, String ext) {
        Spider spider = spiders.get(key);
        if (spider != null) return spider;
        synchronized (this) {
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
            Spider spider = loader.spider(App.get(), api);
            spider.siteKey = key;
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
