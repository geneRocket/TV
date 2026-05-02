package com.fongmi.android.tv.api.loader;

import android.text.TextUtils;

import com.fongmi.android.tv.api.config.LiveConfig;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.Live;
import com.fongmi.android.tv.bean.Site;
import com.github.catvod.crawler.Spider;
import com.github.catvod.crawler.SpiderNull;
import com.github.catvod.utils.Util;

import org.json.JSONObject;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import dalvik.system.DexClassLoader;

public class BaseLoader {

    private final JarLoader jarLoader;
    private final PyLoader pyLoader;
    private final JsLoader jsLoader;

    private static class Loader {
        static volatile BaseLoader INSTANCE = new BaseLoader();
    }

    public static BaseLoader get() {
        return Loader.INSTANCE;
    }

    private BaseLoader() {
        this.jarLoader = new JarLoader();
        this.pyLoader = new PyLoader();
        this.jsLoader = new JsLoader();
    }

    private String siteKey(String key, String api, String ext) {
        return "site:" + key + ":" + Util.md5(api + '\n' + ext);
    }

    private String liveKey(String key, String api, String ext) {
        return "live:" + key + ":" + Util.md5(api + '\n' + ext);
    }

    public void clear() {
        this.jarLoader.clear();
        this.pyLoader.clear();
        this.jsLoader.clear();
    }

    public void clear(String key) {
        this.jarLoader.clear(key);
        this.pyLoader.clear(key);
        this.jsLoader.clear(key);
    }

    public void clearLive(String key, String api, String ext, String jar) {
        clear(liveKey(key, api, ext));
    }

    public Spider getSpider(String key, String api, String ext, String jar) {
        boolean js = api.contains(".js");
        boolean py = api.contains(".py");
        boolean csp = api.startsWith("csp_");
        if (py) return pyLoader.getSpider(key, api, ext);
        else if (js) return jsLoader.getSpider(key, api, ext);
        else if (csp) return jarLoader.getSpider(key, api, ext, jar);
        else return new SpiderNull();
    }

    public Spider getSiteSpider(String key, String api, String ext, String jar) {
        return getSpider(siteKey(key, api, ext), api, ext, jar);
    }

    public Spider getLiveSpider(String key, String api, String ext, String jar) {
        return getSpider(liveKey(key, api, ext), api, ext, jar);
    }

    public Spider getSpider(Map<String, String> params) {
        if (!params.containsKey("siteKey")) return new SpiderNull();
        return getSpider(params.get("siteKey"));
    }

    public Spider getSpider(String key) {
        Spider cached = getCached(key);
        if (cached != null) return cached;
        Site site = VodConfig.get().getSite(key);
        Live live = LiveConfig.get().getLive(key);
        if (!site.isEmpty()) return site.spider();
        if (!live.isEmpty()) return live.spider();
        return new SpiderNull();
    }

    private Spider getCached(String key) {
        Spider spider = jarLoader.getCached(key);
        if (spider != null) return spider;
        spider = jsLoader.getCached(key);
        if (spider != null) return spider;
        return pyLoader.getCached(key);
    }

    public void setRecent(String key, String api, String jar) {
        boolean js = api.contains(".js");
        boolean py = api.contains(".py");
        boolean csp = api.startsWith("csp_");
        if (js) jsLoader.setRecent(key);
        else if (py) pyLoader.setRecent(key);
        else if (csp) jarLoader.setRecent(Util.md5(jar));
    }

    public void setSiteRecent(String key, String api, String ext, String jar) {
        setRecent(siteKey(key, api, ext), api, jar);
    }

    public void setLiveRecent(String key, String api, String ext, String jar) {
        setRecent(liveKey(key, api, ext), api, jar);
    }

    public Object[] proxyLocal(Map<String, String> params) {
        try {
            if (params.containsKey("siteKey")) return getSpider(params.get("siteKey")).proxy(params);
            if ("js".equals(params.get("do"))) return jsLoader.proxyInvoke(params);
            if ("py".equals(params.get("do"))) return pyLoader.proxyInvoke(params);
            return jarLoader.proxyInvoke(params);
        } catch (Throwable e) {
            e.printStackTrace();
            return null;
        }
    }

    public void parseJar(String jar) {
        parseJar(jar, false);
    }

    public void parseJar(String jar, boolean recent) {
        if (TextUtils.isEmpty(jar)) return;
        jarLoader.parseJar(Util.md5(jar), jar);
        if (recent) jarLoader.setRecent(Util.md5(jar));
    }

    public DexClassLoader dex(String jar) {
        return jarLoader.dex(jar);
    }

    public JSONObject jsonExt(String key, LinkedHashMap<String, String> jxs, String url) throws Throwable {
        return jarLoader.jsonExt(key, jxs, url);
    }

    public JSONObject jsonExtMix(String flag, String key, String name, LinkedHashMap<String, HashMap<String, String>> jxs, String url) throws Throwable {
        return jarLoader.jsonExtMix(flag, key, name, jxs, url);
    }
}
