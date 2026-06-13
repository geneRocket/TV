package com.fongmi.android.tv.api.loader;

import android.content.Context;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.api.Decoder;
import com.fongmi.android.tv.utils.UrlUtil;
import com.github.catvod.crawler.Spider;
import com.github.catvod.crawler.SpiderNull;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Path;
import com.github.catvod.utils.Util;

import org.json.JSONObject;

import java.io.File;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import dalvik.system.DexClassLoader;
import okhttp3.Response;

public class JarLoader {

    private final ConcurrentHashMap<String, DexClassLoader> loaders;
    private final ConcurrentHashMap<String, Method> methods;
    private final ConcurrentHashMap<String, Spider> spiders;
    private final ConcurrentHashMap<String, Object> locks;
    private String recent;

    public JarLoader() {
        loaders = new ConcurrentHashMap<>();
        methods = new ConcurrentHashMap<>();
        spiders = new ConcurrentHashMap<>();
        locks = new ConcurrentHashMap<>();
    }

    public void clear() {
        for (Spider spider : spiders.values()) App.execute(spider::destroy);
        loaders.clear();
        methods.clear();
        spiders.clear();
        locks.clear();
        recent = null;
    }

    public void clear(String key) {
        ConcurrentHashMap<String, Boolean> jars = new ConcurrentHashMap<>();
        spiders.entrySet().removeIf(entry -> {
            Spider spider = entry.getValue();
            if (spider == null || !key.equals(spider.siteKey)) return false;
            locks.remove(entry.getKey());
            if (entry.getKey().length() > 32) jars.put(entry.getKey().substring(0, 32), true);
            App.execute(spider::destroy);
            return true;
        });
        for (String jarKey : jars.keySet()) {
            if (spiders.keySet().stream().noneMatch(spiderKey -> spiderKey.startsWith(jarKey))) {
                loaders.remove(jarKey);
                methods.remove(jarKey);
                locks.remove(jarKey);
                if (jarKey.equals(recent)) recent = null;
            }
        }
    }

    public void setRecent(String recent) {
        this.recent = recent;
    }

    private void load(String key, File file) {
        if (Thread.interrupted()) return;
        if (!file.exists()) return;
        file.setReadOnly();
        String cachePath = Path.jar().getAbsolutePath();
        DexClassLoader loader = new DexClassLoader(file.getAbsolutePath(), cachePath, cachePath, App.get().getClassLoader());
        invokeInit(loader);
        putProxy(key, loader);
        loaders.put(key, loader);
    }

    private void invokeInit(DexClassLoader loader) {
        try {
            Class<?> clz = loader.loadClass("com.github.catvod.spider.Init");
            Method method = clz.getMethod("init", Context.class);
            method.invoke(clz, App.get());
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    private void putProxy(String key, DexClassLoader loader) {
        try {
            Class<?> clz = loader.loadClass("com.github.catvod.spider.Proxy");
            Method method = clz.getMethod("proxy", Map.class);
            methods.put(key, method);
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    private File download(String url) {
        try {
            try (Response response = OkHttp.newCall(url).execute()) {
                if (!response.isSuccessful()) return Path.jar(url);
                return response.body() == null ? Path.jar(url) : Path.write(Path.jar(url), response.body().bytes());
            }
        } catch (Exception e) {
            return Path.jar(url);
        }
    }

    public void parseJar(String key, String jar) {
        if (loaders.containsKey(key) || jar.isEmpty()) return;
        if (jar.startsWith("assets")) jar = UrlUtil.convert(jar);
        String[] texts = jar.split(";md5;");
        String md5Url = texts.length > 1 ? texts[1].trim() : "";
        String jarUrl = texts[0];
        
        File file = Path.jar(jarUrl);
        if (file.exists() && file.length() > 0) {
            synchronized (locks.computeIfAbsent(key, k -> new Object())) {
                if (!loaders.containsKey(key)) load(key, file);
            }
            if (jarUrl.startsWith("http")) App.execute(() -> checkUpdate(jarUrl, md5Url));
            return;
        }

        App.execute(() -> {
            String md5 = md5Url.startsWith("http") ? OkHttp.string(md5Url, 5000).trim() : md5Url;
            synchronized (locks.computeIfAbsent(key, k -> new Object())) {
                if (loaders.containsKey(key)) return;
                if (md5.length() > 0 && Util.equals(jarUrl, md5)) {
                    load(key, Path.jar(jarUrl));
                } else if (jarUrl.startsWith("img+")) {
                    load(key, Decoder.getSpider(jarUrl));
                } else if (jarUrl.startsWith("http")) {
                    load(key, download(jarUrl));
                } else if (jarUrl.startsWith("file")) {
                    load(key, Path.local(jarUrl));
                } else {
                    parseJar(key, UrlUtil.convert(jarUrl));
                }
            }
        });
    }

    private void checkUpdate(String jarUrl, String md5Url) {
        try {
            String md5 = md5Url.startsWith("http") ? OkHttp.string(md5Url, 5000).trim() : md5Url;
            if (md5.length() > 0 && Util.equals(jarUrl, md5)) return;
            download(jarUrl);
        } catch (Throwable ignored) {
        }
    }

    public DexClassLoader dex(String jar) {
        try {
            String key = Util.md5(jar);
            parseJar(key, jar);
            return loaders.get(key);
        } catch (Throwable e) {
            e.printStackTrace();
            return null;
        }
    }

    public Spider getSpider(String key, String api, String ext, String jar) {
        String jaKey = Util.md5(jar);
        String spKey = jaKey + key;
        Spider spider = spiders.get(spKey);
        if (spider != null) return spider;
        synchronized (locks.computeIfAbsent(spKey, k -> new Object())) {
            spider = spiders.get(spKey);
            if (spider != null) return spider;
            try {
                parseJar(jaKey, jar);
                DexClassLoader loader = loaders.get(jaKey);
                if (loader == null) return new SpiderNull();
                String spiderName = getSpiderName(api);
                if (spiderName.isEmpty()) return new SpiderNull();
                spider = (Spider) loader.loadClass("com.github.catvod.spider." + spiderName).newInstance();
                spider.siteKey = key;
                spider.init(App.get(), ext);
                spiders.put(spKey, spider);
                return spider;
            } catch (Throwable e) {
                e.printStackTrace();
                return new SpiderNull();
            }
        }
    }

    public Spider getCached(String key) {
        for (Spider spider : spiders.values()) if (spider != null && key.equals(spider.siteKey)) return spider;
        return null;
    }

    private String getSpiderName(String api) {
        int index = api.indexOf("csp_");
        if (index < 0) return "";
        return api.substring(index + 4).trim();
    }

    public JSONObject jsonExt(String key, LinkedHashMap<String, String> jxs, String url) throws Throwable {
        DexClassLoader loader = loaders.get(recent);
        if (loader == null) throw new IllegalStateException("Missing jar loader for recent key: " + recent);
        Class<?> clz = loader.loadClass("com.github.catvod.parser.Json" + key);
        Method method = clz.getMethod("parse", LinkedHashMap.class, String.class);
        return (JSONObject) method.invoke(null, jxs, url);
    }

    public JSONObject jsonExtMix(String flag, String key, String name, LinkedHashMap<String, HashMap<String, String>> jxs, String url) throws Throwable {
        DexClassLoader loader = loaders.get(recent);
        if (loader == null) throw new IllegalStateException("Missing jar loader for recent key: " + recent);
        Class<?> clz = loader.loadClass("com.github.catvod.parser.Mix" + key);
        Method method = clz.getMethod("parse", LinkedHashMap.class, String.class, String.class, String.class);
        return (JSONObject) method.invoke(null, jxs, name, flag, url);
    }

    public Object[] proxyInvoke(Map<String, String> params) {
        Method method = recent != null ? methods.get(recent) : null;
        Object[] result = proxyInvoke(method, params);
        if (result != null) return result;
        return tryOthers(params);
    }

    private Object[] tryOthers(Map<String, String> params) {
        return methods.entrySet().stream().filter(e -> !e.getKey().equals(recent)).map(e -> proxyInvoke(e.getValue(), params)).filter(Objects::nonNull).findFirst().orElse(null);
    }

    private Object[] proxyInvoke(Method method, Map<String, String> params) {
        try {
            return method == null ? null : (Object[]) method.invoke(null, params);
        } catch (Throwable e) {
            e.printStackTrace();
            return null;
        }
    }
}
