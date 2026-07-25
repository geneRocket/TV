package com.fongmi.android.tv.api.loader;

import com.fongmi.android.tv.App;
import com.fongmi.chaquo.Loader;
import com.github.catvod.crawler.Spider;
import com.github.catvod.crawler.SpiderNull;

import java.util.Map;

public class PyLoader {

    private final SpiderStore spiders;
    private final Loader loader;

    public PyLoader() {
        spiders = new SpiderStore();
        loader = new Loader();
    }

    public void clear() {
        spiders.clear();
    }

    public void clear(String key) {
        spiders.clear(key);
    }

    public void setRecent(String recent) {
        spiders.setRecent(recent);
    }

    public Spider getSpider(String key, String api, String ext) {
        Spider spider = spiders.getOrCreate(key, () -> createSpider(key, api, ext));
        return spider == null ? new SpiderNull() : spider;
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
            return null;
        }
    }

    public Object[] proxyInvoke(Map<String, String> params) {
        try {
            if (!params.containsKey("siteKey")) {
                Spider spider = spiders.getRecent();
                return spider == null ? null : spider.proxy(params);
            }
            return BaseLoader.get().getSpider(params).proxy(params);
        } catch (Throwable e) {
            e.printStackTrace();
            return null;
        }
    }

}
