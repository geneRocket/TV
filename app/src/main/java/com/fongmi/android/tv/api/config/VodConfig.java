package com.fongmi.android.tv.api.config;

import android.text.TextUtils;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.Setting;
import com.fongmi.android.tv.api.Decoder;
import com.fongmi.android.tv.api.loader.BaseLoader;
import com.fongmi.android.tv.bean.Config;
import com.fongmi.android.tv.repository.ConfigRepository;
import com.fongmi.android.tv.bean.Depot;
import com.fongmi.android.tv.bean.Parse;
import com.fongmi.android.tv.bean.Rule;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.repository.SiteRepository;
import com.fongmi.android.tv.impl.Callback;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.ThreadPools;
import com.fongmi.android.tv.utils.UrlUtil;
import com.github.catvod.bean.Doh;
import com.github.catvod.bean.Header;
import com.github.catvod.bean.Proxy;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Json;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import okhttp3.HttpUrl;

public class VodConfig {

    private List<Doh> doh;
    private List<Rule> rules;
    private List<Site> sites;
    private List<Parse> parses;
    private List<String> flags;
    private List<String> ads;
    private List<Header> headers;
    private List<Proxy> proxy;
    private List<String> hosts;
    private List<String> ruleHosts;
    private List<String> loadUrls;
    private Map<String, Site> siteMap;
    private Map<String, Parse> parseMap;
    private boolean loadLive;
    private boolean persistCache;
    private Config config;
    private Parse parse;
    private String wall;
    private Site home;

    private static class Loader {
        static volatile VodConfig INSTANCE = new VodConfig();
    }

    public static VodConfig get() {
        return Loader.INSTANCE;
    }

    public static int getCid() {
        return get().getConfig().getId();
    }

    public static String getUrl() {
        return get().getConfig().getUrl();
    }

    public static String getDesc() {
        return get().getConfig().getDesc();
    }

    public static String siteKey(int cid, String key) {
        if (TextUtils.isEmpty(key) || "all".equals(key)) return key == null ? "" : key;
        if (isScopedSiteKey(key)) return key;
        cid = cid > 0 ? cid : getCid();
        return cid + "@" + key;
    }

    public static String siteKey(String key) {
        return siteKey(getCid(), key);
    }

    public static String rawSiteKey(String key) {
        if (!isScopedSiteKey(key)) return key == null ? "" : key;
        return key.substring(key.indexOf('@') + 1);
    }

    public static boolean isScopedSiteKey(String key) {
        if (TextUtils.isEmpty(key)) return false;
        int index = key.indexOf('@');
        if (index <= 0) return false;
        for (int i = 0; i < index; i++) if (!Character.isDigit(key.charAt(i))) return false;
        return true;
    }

    public static int siteCid(String key, int fallbackCid) {
        if (!isScopedSiteKey(key)) return fallbackCid;
        return Integer.parseInt(key.substring(0, key.indexOf('@')));
    }

    public static int getHomeIndex() {
        return get().getSites().indexOf(get().getHome());
    }

    public static boolean hasUrl() {
        return getUrl() != null && getUrl().length() > 0;
    }

    public static boolean hasParse() {
        return !get().getParses().isEmpty();
    }

    public static void load(Config config, Callback callback) {
        get().submit(() -> get().init().clear().config(config).loadConfig(callback));
    }

    public static void load(Config config, Callback callback, boolean loadLive) {
        get().submit(() -> {
            VodConfig target = get().init().clear().config(config);
            if (loadLive) target.loadConfigCache(callback);
            else target.loadConfig(callback);
        });
    }

    public static void load(List<Config> configs, Callback callback) {
        if (configs == null || configs.isEmpty()) return;
        get().submit(() -> get().init().clear().config(configs.get(0)).setPersistCache(false).loadConfigs(configs, callback));
    }

    public static void load(List<Config> configs, Callback callback, boolean loadLive) {
        if (configs == null || configs.isEmpty()) return;
        get().submit(() -> get().init().clear().config(configs.get(0)).setLoadLive(loadLive).setPersistCache(false).loadConfigs(configs, callback));
    }

    public static void load(List<Config> configs, Callback callback, boolean loadLive, boolean cache) {
        if (configs == null || configs.isEmpty()) return;
        get().submit(() -> {
            VodConfig target = get().init().clear().config(configs.get(0)).setLoadLive(loadLive).setPersistCache(false);
            if (cache) target.loadConfigsCache(configs, callback);
            else target.loadConfigs(configs, callback);
        });
    }

    public static void reload(Callback callback) {
        get().submit(() -> get().init().clear().config(Config.vod()).loadConfig(callback));
    }

    /** Clears shared state after already queued configuration work has finished. */
    public static void release() {
        get().submit(get()::clear);
    }

    private void submit(Runnable task) {
        ThreadPools.configLoad().execute(() -> {
            synchronized (this) {
                task.run();
            }
        });
    }

    private VodConfig setLoadLive(boolean loadLive) {
        this.loadLive = loadLive;
        return this;
    }

    private VodConfig setPersistCache(boolean persistCache) {
        this.persistCache = persistCache;
        return this;
    }

    public synchronized VodConfig init() {
        this.wall = null;
        this.home = null;
        this.parse = null;
        this.config = new Config();
        if (this.ads == null) this.ads = new ArrayList<>();
        if (this.doh == null) this.doh = new ArrayList<>();
        if (this.hosts == null) this.hosts = new ArrayList<>();
        if (this.proxy == null) this.proxy = new ArrayList<>();
        if (this.rules == null) this.rules = new ArrayList<>();
        if (this.headers == null) this.headers = new ArrayList<>();
        if (this.ruleHosts == null) this.ruleHosts = new ArrayList<>();
        if (this.loadUrls == null) this.loadUrls = new ArrayList<>();
        if (this.siteMap == null) this.siteMap = new HashMap<>();
        if (this.parseMap == null) this.parseMap = new HashMap<>();
        if (this.sites == null) this.sites = new ArrayList<>();
        if (this.flags == null) this.flags = new ArrayList<>();
        if (this.parses == null) this.parses = new ArrayList<>();
        this.loadLive = false;
        this.persistCache = true;
        return this;
    }

    public synchronized VodConfig config(Config config) {
        this.config = config;
        return this;
    }

    public synchronized VodConfig clear() {
        this.wall = null;
        this.home = null;
        this.parse = null;
        this.ads.clear();
        this.doh.clear();
        this.hosts.clear();
        this.proxy.clear();
        this.rules.clear();
        this.headers.clear();
        this.ruleHosts.clear();
        this.loadUrls.clear();
        this.siteMap.clear();
        this.parseMap.clear();
        this.sites.clear();
        this.flags.clear();
        this.parses.clear();
        this.loadLive = true;
        BaseLoader.get().clear();
        return this;
    }

    public void load(Callback callback) {
        load(callback, false);
    }

    public void load(Callback callback, boolean cache) {
        submit(() -> {
            if (cache) loadConfigCache(callback);
            else loadConfig(callback);
        });
    }

    public void loadMulti(List<Config> configs, Callback callback) {
        this.persistCache = false;
        submit(() -> loadConfigs(configs, callback));
    }

    public void loadMulti(List<Config> configs, Callback callback, boolean loadLive) {
        this.loadLive = loadLive;
        this.persistCache = false;
        submit(() -> loadConfigs(configs, callback));
    }

    public void loadMulti(List<Config> configs, Callback callback, boolean loadLive, boolean cache) {
        this.loadLive = loadLive;
        this.persistCache = false;
        submit(() -> {
            if (cache) loadConfigsCache(configs, callback);
            else loadConfigs(configs, callback);
        });
    }

    private void loadConfig(Callback callback) {
        try {
            if (config.isEmpty()) config = Config.vod();
            setLoadUrls(Collections.singletonList(config.getUrl()));
            checkJson(Json.parse(Decoder.getJson(config.getUrl())).getAsJsonObject(), callback);
        } catch (Throwable e) {
            if (config.isEmpty() || TextUtils.isEmpty(config.getUrl())) App.post(() -> callback.error(""));
            else loadCache(callback, e);
            e.printStackTrace();
        }
    }

    private void loadConfigs(List<Config> configs, Callback callback) {
        List<String> urls = new ArrayList<>();
        JsonObject merged = new JsonObject();
        Throwable error = null;
        int success = 0;
        for (ConfigResult result : loadConfigResults(configs, false)) {
            try {
                if (result.error != null) throw result.error;
                if (result.cacheable) cacheConfig(result.config, result.object);
                urls.add(result.config.getUrl());
                mergeConfig(merged, result.object, result.config);
                success++;
            } catch (Throwable e) {
                error = e;
                e.printStackTrace();
            }
        }
        setLoadUrls(urls);
        if (success > 0) {
            parseConfig(merged, callback);
        }
        else if (!TextUtils.isEmpty(config.getJson())) checkJson(Json.parse(config.getJson()).getAsJsonObject(), callback);
        else {
            Throwable cause = error == null ? new Throwable("No valid config") : error;
            App.post(() -> callback.error(Notify.getError(R.string.error_config_get, cause)));
        }
    }

    private void loadConfigsCache(List<Config> configs, Callback callback) {
        setLoadUrls(getConfigUrls(configs));
        List<String> urls = new ArrayList<>();
        JsonObject merged = new JsonObject();
        Throwable error = null;
        int success = 0;
        for (ConfigResult result : loadConfigResults(configs, true)) {
            try {
                if (result.error != null) throw result.error;
                if (result.cacheable) cacheConfig(result.config, result.object);
                urls.add(result.config.getUrl());
                mergeConfig(merged, result.object, result.config);
                success++;
            } catch (Throwable e) {
                error = e;
                e.printStackTrace();
            }
        }
        setLoadUrls(urls);
        if (success > 0) {
            parseConfig(merged, callback);
        } else if (!TextUtils.isEmpty(config.getJson())) {
            checkJson(Json.parse(config.getJson()).getAsJsonObject(), callback);
        } else {
            Throwable cause = error == null ? new Throwable("No valid config") : error;
            App.post(() -> callback.error(Notify.getError(R.string.error_config_get, cause)));
        }
    }

    private List<ConfigResult> loadConfigResults(List<Config> configs, boolean cache) {
        List<Config> unique = getUniqueConfigs(configs);
        if (unique.isEmpty()) return Collections.emptyList();
        ExecutorService executor = ThreadPools.newFixed("vod-config", Math.min(unique.size(), com.fongmi.android.tv.Constant.THREAD_POOL));
        List<Future<ConfigResult>> futures = new ArrayList<>();
        List<ConfigResult> results = new ArrayList<>();
        try {
            for (Config item : unique) futures.add(executor.submit(() -> loadConfigResult(item, cache)));
            for (Future<ConfigResult> future : futures) {
                try {
                    results.add(future.get());
                } catch (Throwable e) {
                    e.printStackTrace();
                }
            }
        } catch (Throwable e) {
            e.printStackTrace();
        } finally {
            ThreadPools.shutdown(executor);
        }
        return results;
    }

    private ConfigResult loadConfigResult(Config item, boolean cache) {
        try {
            if (cache && !TextUtils.isEmpty(item.getJson())) {
                if (!item.isCache()) ThreadPools.config().execute(() -> { try { cacheConfig(item, loadObject(item.getUrl(), 0)); } catch (Throwable ignored) {} });
                return ConfigResult.success(item, Json.parse(item.getJson()).getAsJsonObject(), false);
            }
            return ConfigResult.success(item, loadObject(item.getUrl(), 0), true);
        } catch (Throwable e) {
            if (!TextUtils.isEmpty(item.getJson())) {
                try {
                    return ConfigResult.success(item, Json.parse(item.getJson()).getAsJsonObject(), false);
                } catch (Throwable ignored) {
                    // Fall through to the original fetch error.
                }
            }
            return ConfigResult.error(item, e);
        }
    }

    private List<Config> getUniqueConfigs(List<Config> configs) {
        List<Config> items = new ArrayList<>();
        Set<String> loaded = new LinkedHashSet<>();
        for (Config item : configs) {
            String url = normalizeConfigUrl(item.getUrl());
            if (TextUtils.isEmpty(url) || !loaded.add(url)) continue;
            if (!url.equals(item.getUrl())) item.url(url);
            items.add(item);
        }
        return items;
    }

    private List<String> getConfigUrls(List<Config> configs) {
        List<String> urls = new ArrayList<>();
        Set<String> loaded = new LinkedHashSet<>();
        for (Config item : configs) {
            String url = normalizeConfigUrl(item.getUrl());
            if (TextUtils.isEmpty(url) || !loaded.add(url)) continue;
            urls.add(url);
        }
        return urls;
    }

    private void cacheConfig(Config item, JsonObject object) {
        if (item == null || object == null || TextUtils.isEmpty(item.getUrl())) return;
        item.json(object.toString()).time(System.currentTimeMillis()).save();
    }

    private void setLoadUrls(List<String> urls) {
        this.loadUrls.clear();
        if (urls == null) return;
        Set<String> loaded = new LinkedHashSet<>();
        for (String url : urls) {
            String value = normalizeConfigUrl(url);
            if (!TextUtils.isEmpty(value) && loaded.add(value)) this.loadUrls.add(value);
        }
    }

    private String normalizeConfigUrl(String url) {
        return url == null ? "" : url.trim();
    }

    private JsonObject loadObject(String url, int depth) throws Throwable {
        if (depth > 5) throw new IllegalStateException("Too many redirects.");
        JsonObject object = Json.parse(Decoder.getJson(url)).getAsJsonObject();
        if (object.has("msg")) throw new IllegalStateException(object.get("msg").getAsString());
        if (!object.has("urls")) return object;
        List<Depot> items = Depot.arrayFrom(object.getAsJsonArray("urls").toString());
        if (items.isEmpty()) throw new IllegalStateException(ResUtil.getString(R.string.error_config_parse));
        return loadDepot(items, depth + 1);
    }

    private JsonObject loadDepot(List<Depot> items, int depth) throws Throwable {
        Throwable error = null;
        for (Depot item : items) {
            try {
                return loadObject(item.getUrl(), depth);
            } catch (Throwable e) {
                error = e;
                e.printStackTrace();
            }
        }
        if (error != null) throw error;
        throw new IllegalStateException(ResUtil.getString(R.string.error_config_parse));
    }

    private void mergeConfig(JsonObject target, JsonObject source, Config config) {
        JsonObject root = source.has("video") ? source.getAsJsonObject("video") : source;
        mergeSites(target, root, config);
        appendArray(target, source, "lives");
        if (root != source) appendArray(target, root, "lives");
        appendArray(target, root, "parses");
        appendArray(target, root, "rules");
        appendArray(target, root, "doh");
        appendArray(target, root, "headers");
        appendArray(target, root, "proxy");
        appendArray(target, root, "hosts");
        appendArray(target, root, "flags");
        appendArray(target, root, "ads");
        copyIfEmpty(target, root, "notice");
        copyIfEmpty(target, root, "logo");
        copyIfEmpty(target, root, "wallpaper");
        copyIfEmpty(target, root, "spider");
    }

    private void mergeSites(JsonObject target, JsonObject source, Config config) {
        String spider = Json.safeString(source, "spider");
        JsonArray sites = source.has("sites") ? source.getAsJsonArray("sites") : new JsonArray();
        if (!target.has("sites")) target.add("sites", new JsonArray());
        JsonArray targetSites = target.getAsJsonArray("sites");
        for (JsonElement element : sites) {
            JsonObject item = element.getAsJsonObject().deepCopy();
            String api = Json.safeString(item, "api");
            String jar = Json.safeString(item, "jar");
            String key = Json.safeString(item, "key");
            if (TextUtils.isEmpty(jar) && api.startsWith("csp_")) item.addProperty("jar", spider);
            item.addProperty("key", siteKey(config.getId(), key));
            targetSites.add(item);
        }
    }

    private void appendArray(JsonObject target, JsonObject source, String key) {
        if (!source.has(key)) return;
        if (!target.has(key)) target.add(key, new JsonArray());
        JsonArray targetArray = target.getAsJsonArray(key);
        for (JsonElement element : source.getAsJsonArray(key)) targetArray.add(element.deepCopy());
    }

    private void copyIfEmpty(JsonObject target, JsonObject source, String key) {
        if (target.has(key)) return;
        if (source.has(key)) target.add(key, source.get(key).deepCopy());
    }

    private void loadCache(Callback callback, Throwable e) {
        if (!TextUtils.isEmpty(config.getJson())) checkJson(Json.parse(config.getJson()).getAsJsonObject(), callback);
        else App.post(() -> callback.error(Notify.getError(R.string.error_config_get, e)));
    }

    private void loadConfigCache(Callback callback) {
        if (config.isEmpty()) config = Config.vod();
        if (!TextUtils.isEmpty(config.getJson())) {
            Config target = config;
            parseConfig(config.getJson(), callback);
            if (!config.isCache()) ThreadPools.config().execute(() -> {
                try {
                    JsonObject object = loadObject(target.getUrl(), 0);
                    cacheConfig(target, object);
                } catch (Throwable ignored) {
                }
            });
        } else {
            loadConfig(callback);
        }
    }

    private void checkJson(JsonObject object, Callback callback) {
        if (object.has("msg") && callback != null) {
            App.post(() -> callback.error(object.get("msg").getAsString()));
        } else if (object.has("urls")) {
            parseDepot(object, callback);
        } else {
            parseConfig(object, callback);
        }
    }

    private void parseDepot(JsonObject object, Callback callback) {
        List<Depot> items = Depot.arrayFrom(object.getAsJsonArray("urls").toString());
        if (items.isEmpty()) {
            if (callback != null) App.post(() -> callback.error(ResUtil.getString(R.string.error_config_parse)));
            return;
        }
        ConfigRepository.get().delete(config.getUrl());
        Throwable error = null;
        for (Depot item : items) {
            try {
                Config target = ConfigRepository.get().find(item, 0);
                JsonObject loaded = loadDepotObject(target);
                config = target;
                setLoadUrls(Collections.singletonList(target.getUrl()));
                checkJson(loaded, callback);
                return;
            } catch (Throwable e) {
                error = e;
                e.printStackTrace();
            }
        }
        Throwable cause = error == null ? new Throwable("No valid config") : error;
        if (callback != null) App.post(() -> callback.error(Notify.getError(R.string.error_config_get, cause)));
    }

    private JsonObject loadDepotObject(Config target) throws Throwable {
        if (!TextUtils.isEmpty(target.getJson())) {
            if (!target.isCache()) ThreadPools.config().execute(() -> refreshDepotCache(target));
            return Json.parse(target.getJson()).getAsJsonObject();
        }
        JsonObject loaded = loadObject(target.getUrl(), 0);
        cacheConfig(target, loaded);
        return loaded;
    }

    private void refreshDepotCache(Config target) {
        try {
            cacheConfig(target, loadObject(target.getUrl(), 0));
        } catch (Throwable ignored) {
        }
    }

    private void parseConfig(String text, Callback callback) {
        try {
            parseConfig(Json.parse(text).getAsJsonObject(), callback);
        } catch (Throwable e) {
            e.printStackTrace();
            if (callback != null) App.post(() -> callback.error(Notify.getError(R.string.error_config_parse, e)));
        }
    }

    private synchronized void parseConfig(JsonObject object, Callback callback) {
        try {
            if (object.has("urls")) {
                parseDepot(object, callback);
                return;
            }
            initSite(object);
            initParse(object);
            initOther(object);
            preloadJars(object);
            if (loadLive && object.has("lives")) initLive(object);
            String notice = Json.safeString(object, "notice");
            config.logo(Json.safeString(object, "logo"));
            if (persistCache) config.json(object.toString()).update();
            postSuccess(callback, notice);
        } catch (Throwable e) {
            e.printStackTrace();
            if (callback != null) App.post(() -> callback.error(Notify.getError(R.string.error_config_parse, e)));
        }
    }

    private void postSuccess(Callback callback, String notice) {
        if (callback == null) return;
        if (TextUtils.isEmpty(notice)) App.post(callback::success);
        else App.post(() -> callback.success(notice));
    }

    private void initSite(JsonObject object) {
        if (object.has("video")) {
            initSite(object.getAsJsonObject("video"));
            return;
        }
        String spider = Json.safeString(object, "spider");
        List<JsonElement> elements = Json.safeListElement(object, "sites");
        Map<String, Site> cache = new HashMap<>();
        for (Site s : SiteRepository.get().all()) cache.put(s.getKey(), s);
        for (JsonElement element : elements) {
            Site site = Site.objectFrom(element, spider);
            if (!site.isEmpty() && !isScopedSiteKey(site.getKey())) site.setKey(siteKey(config.getId(), site.getKey()));
            if (siteMap.containsKey(site.getKey())) {
                Site old = siteMap.get(site.getKey());
                if (old != null) {
                    old.setApi(site.getApi());
                    old.setExt(site.getExt());
                    old.setJar(site.getJar());
                    old.setSpider(null);
                }
                continue;
            }
            site.setJar(parseJar(site, spider));
            sites.add(site.trans().sync(cache));
            siteMap.put(site.getKey(), site);
        }
        for (Site site : sites) {
            if (site.getKey().equals(config.getHome()) || site.getKey().equals(siteKey(config.getId(), config.getHome()))) {
                setHome(site);
            }
        }
    }

    private void preloadJars(JsonObject object) {
        Set<String> jars = new LinkedHashSet<>();
        jars.add(Json.safeString(object, "spider"));
        for (Site site : sites) jars.add(site.getJar());
        for (String jar : jars) BaseLoader.get().parseJar(jar);
    }

    private void initLive(JsonObject object) {
        Config temp = ConfigRepository.get().find(config, 1).save();
        boolean sync = false;
        for (String url : loadUrls) {
            if (LiveConfig.get().needSync(url)) {
                sync = true;
                break;
            }
        }
        if (loadUrls.isEmpty()) sync = LiveConfig.get().needSync(config.getUrl());
        if (sync) {
            LiveConfig.get().init().clear().config(temp).parse(object);
            putLiveSetting(loadUrls.isEmpty() ? Collections.singletonList(temp.getUrl()) : loadUrls);
        }
    }

    private void putLiveSetting(List<String> urls) {
        List<String> values = new ArrayList<>();
        List<String> names = new ArrayList<>();
        for (String url : urls) {
            if (TextUtils.isEmpty(url) || values.contains(url)) continue;
            values.add(url);
            names.add(ConfigRepository.get().find(url, 1).getDesc());
        }
        if (values.isEmpty()) return;
        StringBuilder sb = new StringBuilder();
        for (String value : values) {
            if (sb.length() > 0) sb.append('\n');
            sb.append(value);
        }
        Setting.putLiveConfigUrls(sb.toString());
        if (names.size() == 1) Setting.putLiveConfigDesc(names.get(0));
        else if (names.size() == 2) Setting.putLiveConfigDesc(names.get(0) + " + " + names.get(1));
        else Setting.putLiveConfigDesc(names.get(0) + " +" + (names.size() - 1));
    }

    private static class ConfigResult {

        private final Config config;
        private final JsonObject object;
        private final Throwable error;
        private final boolean cacheable;

        private ConfigResult(Config config, JsonObject object, Throwable error, boolean cacheable) {
            this.config = config;
            this.object = object;
            this.error = error;
            this.cacheable = cacheable;
        }

        private static ConfigResult success(Config config, JsonObject object, boolean cacheable) {
            return new ConfigResult(config, object, null, cacheable);
        }

        private static ConfigResult error(Config config, Throwable error) {
            return new ConfigResult(config, null, error, false);
        }
    }

    private void initParse(JsonObject object) {
        for (JsonElement element : Json.safeListElement(object, "parses")) {
            Parse parse = Parse.objectFrom(element);
            if (parse.getName().equals(config.getParse()) && parse.getType() > 1) setParse(parse);
            if (!parseMap.containsKey(parse.getName())) {
                parses.add(parse);
                parseMap.put(parse.getName(), parse);
            }
        }
    }

    private void initOther(JsonObject object) {
        if (parses.size() > 0) {
            Parse god = Parse.god();
            parses.add(0, god);
            parseMap.put(god.getName(), god);
        }
        if (home == null) setHome(sites.isEmpty() ? new Site() : sites.get(0));
        if (parse == null) setParse(parses.isEmpty() ? new Parse() : parses.get(0));
        setHeaders(Header.arrayFrom(object.get("headers")));
        setProxy(Proxy.arrayFrom(object.get("proxy")));
        setHosts(Json.safeListString(object, "hosts"));
        setRules(Rule.arrayFrom(object.getAsJsonArray("rules")));
        setDoh(Doh.arrayFrom(object.getAsJsonArray("doh")));
        refreshNetwork();
        setFlags(Json.safeListString(object, "flags"));
        setWall(Json.safeString(object, "wallpaper"));
        setAds(Json.safeListString(object, "ads"));
    }

    private String parseApi(String api) {
        if (api.startsWith("file") || api.startsWith("clan") || api.startsWith("assets")) return UrlUtil.convert(api);
        return api;
    }

    private String parseExt(String ext) {
        if (ext.startsWith("file") || ext.startsWith("clan") || ext.startsWith("assets")) return UrlUtil.convert(ext);
        if (ext.startsWith("img+")) return Decoder.getExt(ext);
        return ext;
    }

    private String parseJar(Site site, String spider) {
        if (site.getJar().isEmpty() && site.getApi().startsWith("csp_")) return spider;
        return site.getJar();
    }

    private void setHeaders(List<Header> headers) {
        this.headers = headers == null ? new ArrayList<>() : new ArrayList<>(headers);
    }

    private void setProxy(List<Proxy> proxy) {
        this.proxy = proxy == null ? new ArrayList<>() : new ArrayList<>(proxy);
    }

    private void setHosts(List<String> hosts) {
        this.hosts = hosts == null ? new ArrayList<>() : new ArrayList<>(hosts);
    }

    static void refreshNetwork() {
        OkHttp.clearConfig();
        OkHttp.get().setProxy(Setting.getProxy());
        OkHttp.get().setDoh(Doh.objectFrom(Setting.getDoh()));
        OkHttp.responseInterceptor().addAll(get().getHeaders());
        OkHttp.responseInterceptor().addAll(LiveConfig.get().getHeaders());
        OkHttp.authenticator().addAll(get().getProxy());
        OkHttp.authenticator().addAll(LiveConfig.get().getProxy());
        OkHttp.selector().addProxyAll(get().getProxy());
        OkHttp.selector().addProxyAll(LiveConfig.get().getProxy());
        OkHttp.dns().addAll(get().getHosts());
        OkHttp.dns().addAll(LiveConfig.get().getHosts());
        OkHttp.selector().addAll(get().getRuleHosts());
        OkHttp.selector().addAll(LiveConfig.get().getRuleHosts());
    }

    public List<Doh> getDoh() {
        List<Doh> items = Doh.get(App.get());
        if (doh == null) return items;
        items.removeAll(doh);
        items.addAll(doh);
        return items;
    }

    public void setDoh(List<Doh> doh) {
        this.doh = doh;
    }

    public List<Rule> getRules() {
        return rules == null ? Collections.emptyList() : rules;
    }

    public void setRules(List<Rule> rules) {
        this.ruleHosts = new ArrayList<>();
        List<Rule> items = rules == null ? new ArrayList<>() : new ArrayList<>(rules);
        for (Rule rule : items) if ("proxy".equals(rule.getName())) ruleHosts.addAll(rule.getHosts());
        items.remove(Rule.create("proxy"));
        this.rules = items;
    }

    public List<Header> getHeaders() {
        return headers == null ? Collections.emptyList() : headers;
    }

    public List<Proxy> getProxy() {
        return proxy == null ? Collections.emptyList() : proxy;
    }

    public List<String> getHosts() {
        return hosts == null ? Collections.emptyList() : hosts;
    }

    public List<String> getRuleHosts() {
        return ruleHosts == null ? Collections.emptyList() : ruleHosts;
    }

    public List<Site> getSites() {
        return sites == null ? Collections.emptyList() : sites;
    }

    public List<Parse> getParses() {
        return parses == null ? Collections.emptyList() : parses;
    }

    public List<Parse> getParses(int type) {
        List<Parse> items = new ArrayList<>();
        for (Parse item : getParses()) if (item.getType() == type) items.add(item);
        return items;
    }

    public List<Parse> getParses(int type, String flag) {
        List<Parse> items = new ArrayList<>();
        for (Parse item : getParses(type)) if (item.getExt().getFlag().isEmpty() || item.getExt().getFlag().contains(flag)) items.add(item);
        if (items.isEmpty()) items.addAll(getParses(type));
        return items;
    }

    public List<String> getFlags() {
        return flags == null ? Collections.emptyList() : flags;
    }

    private void setFlags(List<String> flags) {
        this.flags.addAll(flags);
    }

    public List<String> getAds() {
        return ads == null ? Collections.emptyList() : ads;
    }

    private void setAds(List<String> ads) {
        this.ads = ads;
    }

    public Config getConfig() {
        return config == null ? Config.vod() : config;
    }

    public List<String> getLoadUrls() {
        return loadUrls == null ? new ArrayList<>() : new ArrayList<>(loadUrls);
    }

    public Parse getParse() {
        return parse == null ? new Parse() : parse;
    }

    public Site getHome() {
        return home == null ? new Site() : home;
    }

    public String getWall() {
        return TextUtils.isEmpty(wall) ? "" : wall;
    }

    public Parse getParse(String name) {
        return TextUtils.isEmpty(name) ? null : parseMap.get(name);
    }

    public Site getSite(String key) {
        if (TextUtils.isEmpty(key)) return new Site();
        Site site = siteMap.get(key);
        if (site == null && isScopedSiteKey(key)) site = siteMap.get(rawSiteKey(key));
        if (site == null && !isScopedSiteKey(key)) site = siteMap.get(siteKey(key));
        if (site == null && !isScopedSiteKey(key)) {
            int cid = siteCid(key, getCid());
            for (Site item : getSites()) if (item.getKey().equals(siteKey(cid, key))) return item;
        }
        return site == null ? new Site() : site;
    }

    public boolean hasSite(String key) {
        return !getSite(key).isEmpty();
    }

    public void setParse(Parse parse) {
        this.parse = parse;
        this.parse.setActivated(true);
        config.parse(parse.getName()).save();
        for (Parse item : getParses()) item.setActivated(parse);
    }

    public void setHome(Site home) {
        this.home = home;
        this.home.setActivated(true);
        config.home(isScopedSiteKey(home.getKey()) && siteCid(home.getKey(), config.getId()) != config.getId() ? home.getKey() : rawSiteKey(home.getKey())).save();
        for (Site item : getSites()) item.setActivated(home);
        ThreadPools.config().execute(() -> {
            try {
                String api = home.getApi();
                if (api.startsWith("http")) {
                    HttpUrl url = HttpUrl.parse(api);
                    if (url != null) InetAddress.getAllByName(url.host());
                }
            } catch (Throwable ignored) {
            }
        });
    }

    private void setWall(String wall) {
        this.wall = wall;
        boolean load = !TextUtils.isEmpty(wall) && WallConfig.get().needSync(wall);
        if (load) WallConfig.get().config(ConfigRepository.get().find(wall, config.getName(), 2).update());
    }
}
