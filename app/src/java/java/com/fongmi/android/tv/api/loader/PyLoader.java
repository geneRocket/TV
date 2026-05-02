package com.fongmi.android.tv.api.loader;

import com.github.catvod.crawler.Spider;
import com.github.catvod.crawler.SpiderNull;

import java.util.Map;

public class PyLoader {

    public void clear() {
    }

    public void clear(String key) {
    }

    public void setRecent(String recent) {
    }

    public Spider getSpider(String key, String api, String ext) {
        return new SpiderNull();
    }

    public Spider getCached(String key) {
        return null;
    }

    public Object[] proxyInvoke(Map<String, String> params) {
        return null;
    }
}
