package com.fongmi.android.tv.api.loader;

import android.content.Context;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.api.Decoder;
import com.fongmi.android.tv.utils.ThreadPools;
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
    private volatile String recent;

    public JarLoader() {
        loaders = new ConcurrentHashMap<>();
        methods = new ConcurrentHashMap<>();
        spiders = new ConcurrentHashMap<>();
        locks = new ConcurrentHashMap<>();
    }

    public synchronized void clear() {
        for (Spider spider : spiders.values()) ThreadPools.loader().execute(spider::destroy);
        loaders.clear();
        methods.clear();
        spiders.clear();
        locks.clear();
        recent = null;
    }

    public synchronized void clear(String key) {
        ConcurrentHashMap<String, Boolean> jars = new ConcurrentHashMap<>();
        for (String lockKey : locks.keySet()) {
            if (lockKey.length() > 32 && lockKey.substring(32).equals(key)) {
                locks.remove(lockKey);
                jars.put(lockKey.substring(0, 32), true);
            }
        }
        spiders.entrySet().removeIf(entry -> {
            Spider spider = entry.getValue();
            if (spider == null || !key.equals(spider.siteKey)) return false;
            locks.remove(entry.getKey());
            if (entry.getKey().length() > 32) jars.put(entry.getKey().substring(0, 32), true);
            ThreadPools.loader().execute(spider::destroy);
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

    private synchronized Object lockFor(String key) {
        return locks.computeIfAbsent(key, k -> new Object());
    }

    private synchronized boolean ownsLock(String key, Object lock) {
        return locks.get(key) == lock;
    }

    private void load(String key, File file, Object lock) {
        if (Thread.interrupted()) return;
        if (!ownsLock(key, lock)) return;
        if (!file.exists()) return;
        file.setReadOnly();
        String cachePath = Path.jar().getAbsolutePath();
        DexClassLoader loader = new DexClassLoader(file.getAbsolutePath(), cachePath, cachePath, App.get().getClassLoader());
        invokeInit(loader);
        Method proxy = getProxy(loader);
        synchronized (this) {
            if (!ownsLock(key, lock)) return;
            if (proxy != null) methods.put(key, proxy);
            loaders.put(key, loader);
        }
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

    private Method getProxy(DexClassLoader loader) {
        try {
            Class<?> clz = loader.loadClass("com.github.catvod.spider.Proxy");
            return clz.getMethod("proxy", Map.class);
        } catch (Throwable e) {
            e.printStackTrace();
        }
        return null;
    }

    private void download(String url, File file, String md5) {
        try {
            try (Response response = OkHttp.newCall(url).execute()) {
                if (response.isSuccessful() && response.body() != null) {
                    JarCache.replace(file, response.body().bytes(), md5);
                }
            }
        } catch (Exception error) {
            ThreadPools.log(error, "JAR download failed.");
        }
    }

    public void parseJar(String key, String jar) {
        if (loaders.containsKey(key) || jar.isEmpty()) return;
        if (jar.startsWith("assets")) jar = UrlUtil.convert(jar);
        String[] texts = jar.split(";md5;");
        String md5Url = texts.length > 1 ? texts[1].trim() : "";
        String jarUrl = texts[0];
        Object lock = lockFor(key);

        File file = Path.jar(jarUrl);
        if (md5Url.isEmpty() && file.isFile() && file.length() > 0) {
            synchronized (lock) {
                if (!ownsLock(key, lock)) return;
                if (!loaders.containsKey(key)) load(key, file, lock);
            }
            if (jarUrl.startsWith("http")) ThreadPools.loader().execute(() -> download(jarUrl, file, ""));
            return;
        }

        synchronized (lock) {
            if (!ownsLock(key, lock)) return;
            if (loaders.containsKey(key)) return;
            String md5 = md5Url.startsWith("http") ? OkHttp.string(md5Url, 5000).trim() : md5Url;
            if (jarUrl.startsWith("http")) {
                loadHttpJar(key, jarUrl, md5Url, md5, file, lock);
            } else if (jarUrl.startsWith("img+") || jarUrl.startsWith("file")) {
                File local = jarUrl.startsWith("img+") ? Decoder.getSpider(jarUrl) : Path.local(jarUrl);
                if (md5Url.isEmpty() || (!md5.isEmpty() && Util.md5(local).equalsIgnoreCase(md5))) {
                    load(key, local, lock);
                }
            } else {
                String converted = UrlUtil.convert(jarUrl);
                if (ownsLock(key, lock) && !jarUrl.equals(converted)) {
                    parseJar(key, md5Url.isEmpty() ? converted : converted + ";md5;" + md5Url);
                }
            }
        }
    }

    private void loadHttpJar(String key, String url, String checksumUrl, String checksum, File unpinned, Object lock) {
        if (checksumUrl.isEmpty()) {
            download(url, unpinned, "");
            load(key, unpinned, lock);
            return;
        }
        File verified = Path.jar(url + ";md5;" + checksumUrl);
        if (checksum.isEmpty()) {
            // Only the cache for this descriptor was previously checked against its digest.
            load(key, verified, lock);
            return;
        }
        if (!Util.md5(verified).equalsIgnoreCase(checksum)) {
            download(url, verified, checksum);
        }
        if (Util.md5(verified).equalsIgnoreCase(checksum)) {
            load(key, verified, lock);
        } else if (Util.md5(unpinned).equalsIgnoreCase(checksum)) {
            load(key, unpinned, lock);
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
        Object lock = lockFor(spKey);
        synchronized (lock) {
            if (!ownsLock(spKey, lock)) return new SpiderNull();
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
                synchronized (this) {
                    if (!ownsLock(spKey, lock)) {
                        Spider discarded = spider;
                        ThreadPools.loader().execute(discarded::destroy);
                        return new SpiderNull();
                    }
                    spiders.put(spKey, spider);
                }
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
