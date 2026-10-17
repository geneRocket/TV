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
import com.fongmi.android.tv.event.RefreshEvent;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.OrderedParallelMapper;
import com.fongmi.android.tv.utils.CacheFirstLoader;
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
import java.util.LinkedHashMap;
import java.util.Set;

import okhttp3.HttpUrl;

public class VodConfig {

    private List<Doh> doh = new ArrayList<>();
    private List<Rule> rules = new ArrayList<>();
    private final List<Site> sites = new java.util.concurrent.CopyOnWriteArrayList<>();
    private volatile List<Parse> parses = Collections.emptyList();
    private volatile List<String> flags = new ArrayList<>();
    private List<String> ads = new ArrayList<>();
    private List<Header> headers = new ArrayList<>();
    private List<Proxy> proxy = new ArrayList<>();
    private List<String> hosts = new ArrayList<>();
    private List<String> ruleHosts = new ArrayList<>();
    private List<String> loadUrls = new ArrayList<>();
    private final Map<String, Site> siteMap = new java.util.concurrent.ConcurrentHashMap<>();
    private volatile Map<String, Parse> parseMap = Collections.emptyMap();
    private boolean loadLive = true;
    private boolean persistCache = true;
    private final Map<Integer, JsonObject> loadedObjects = new HashMap<>();
    private List<Config> selectedConfigs = new ArrayList<>();
    private final ConfigLoadTask loads = new ConfigLoadTask();
    private Config config = new Config();
    private Selection selection;
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
        if (key == null || key.isEmpty()) return false;
        int index = key.indexOf('@');
        if (index <= 0) return false;
        for (int i = 0; i < index; i++) if (!Character.isDigit(key.charAt(i))) return false;
        try {
            Integer.parseInt(key.substring(0, index));
            return true;
        } catch (NumberFormatException ignored) {
            return false;
        }
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
        Selection selection = new Selection(config);
        get().submit(() -> {
            VodConfig target = get().reset().config(config);
            target.selection = selection;
            target.loadConfig(callback);
        });
    }

    static final class Selection {
        private final boolean explicit;
        private final long generation;

        Selection(Config requested) {
            explicit = requested != null && !requested.getUrl().isEmpty();
            generation = Setting.beginVodConfigSelection();
        }

        void commit(Config loaded) {
            if (explicit && loaded != null && !loaded.getUrl().isEmpty()) {
                Setting.commitVodConfigSelection(generation, loaded.getUrl(), loaded.getDesc());
            }
        }
    }

    public static void loadCached(Config config, Callback callback) {
        get().submit(() -> {
            VodConfig target = get().reset().config(config);
            target.loadConfigCache(callback);
        });
    }

    public static void load(List<Config> configs, Callback callback) {
        if (configs == null || configs.isEmpty()) return;
        get().submit(() -> get().reset().config(configs.get(0)).setPersistCache(false).loadConfigs(configs, callback));
    }

    public static void loadCached(List<Config> configs, Callback callback) {
        if (configs == null || configs.isEmpty()) return;
        get().submit(() -> get().reset().config(configs.get(0)).setLoadLive(false).setPersistCache(false).loadConfigsCache(configs, callback));
    }

    public static void loadCachedWithLive(List<Config> configs, Callback callback) {
        if (configs == null || configs.isEmpty()) return;
        get().submit(() -> get().reset().config(configs.get(0)).setLoadLive(true).setPersistCache(false).loadConfigsCache(configs, callback));
    }

    public static void reload(Callback callback) {
        get().submit(() -> {
            List<Config> configs = ConfigRepository.get().selected(0);
            VodConfig target = get().reset().config(configs.get(0));
            if (configs.size() == 1) target.loadConfig(callback);
            else target.setPersistCache(false).loadConfigs(configs, callback);
        });
    }

    /** Clears shared state after already queued configuration work has finished. */
    public static void release() {
        get().submit(get()::clear);
    }

    private void submit(Runnable task) {
        loads.submit("vod-config", () -> {
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

    synchronized VodConfig reset() {
        clear();
        this.config = new Config();
        this.persistCache = true;
        return this;
    }

    public synchronized VodConfig config(Config config) {
        this.config = config;
        return this;
    }

    public synchronized VodConfig clear() {
        this.selection = null;
        this.wall = null;
        this.home = null;
        this.loadedObjects.clear();
        this.selectedConfigs.clear();
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
        this.parseMap = Collections.emptyMap();
        this.sites.clear();
        this.flags = new ArrayList<>();
        this.parses = Collections.emptyList();
        this.loadLive = true;
        BaseLoader.get().clear();
        return this;
    }

    private void loadConfig(Callback callback) {
        try {
            if (config.isEmpty()) config = Config.vod();
            setLoadUrls(Collections.singletonList(config.getUrl()));
            JsonObject object = Json.parse(Decoder.getJson(config.getUrl())).getAsJsonObject();
            if (!object.has("urls")) ConfigPayloadValidator.vod(object);
            checkJson(object, callback);
        } catch (Throwable e) {
            ConfigLoadTask.check();
            if (config.isEmpty() || TextUtils.isEmpty(config.getUrl())) ConfigLoadTask.post(() -> callback.error(""));
            else loadCache(callback, e);
            ThreadPools.log(e, "VOD config operation failed.");
        }
    }

    private void loadConfigs(List<Config> configs, Callback callback) {
        loadConfigs(configs, callback, false);
    }

    private void loadConfigsCache(List<Config> configs, Callback callback) {
        setLoadUrls(getConfigUrls(configs));
        loadConfigs(configs, callback, true);
    }

    private void loadConfigs(List<Config> configs, Callback callback, boolean cache) {
        selectedConfigs = new ArrayList<>(configs);
        List<String> urls = new ArrayList<>();
        JsonObject merged = new JsonObject();
        Throwable error = null;
        int success = 0;
        for (ConfigResult result : loadConfigResults(configs, cache)) {
            try {
                if (result.error != null) throw result.error;
                if (result.cacheable) cacheConfig(result.config, result.object);
                loadedObjects.put(result.config.getId(), result.object.deepCopy());
                urls.add(result.config.getUrl());
                mergeConfig(merged, result.object, result.config);
                success++;
            } catch (Throwable e) {
                ConfigLoadTask.check();
                error = e;
                ThreadPools.log(e, "VOD config operation failed.");
            }
        }
        ConfigLoadTask.check();
        setLoadUrls(cache ? getConfigUrls(configs) : urls);
        if (success > 0) {
            parseConfig(merged, callback);
        }
        else if (!TextUtils.isEmpty(config.getJson())) checkJson(Json.parse(config.getJson()).getAsJsonObject(), callback);
        else {
            Throwable cause = error == null ? new Throwable("No valid config") : error;
            ConfigLoadTask.post(() -> callback.error(Notify.getError(R.string.error_config_get, cause)));
        }
    }

    private List<ConfigResult> loadConfigResults(List<Config> configs, boolean cache) {
        List<Config> unique = getUniqueConfigs(configs);
        if (unique.isEmpty()) return Collections.emptyList();
        if (cache) return CacheFirstLoader.load(unique, this::readCachedConfig,
                item -> !item.isCache(), item -> ConfigLoadTask.background(() -> refreshDepotCache(item)),
                this::fetchMissingConfig, () -> loadConfigResults(unique, false));
        try {
            return OrderedParallelMapper.map(ThreadPools.config(), unique, ConfigLoadTask.bind(this::loadConfigResult), error -> ThreadPools.log(error, "VOD config operation failed."));
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            return Collections.emptyList();
        }
    }

    private void fetchMissingConfig(Config item) {
        ConfigLoadTask.background(() -> {
            ConfigResult result = loadConfigResult(item);
            ConfigLoadTask.check();
            if (result.error != null) {
                ThreadPools.log(result.error, "Missing config fetch failed.");
                return;
            }
            if (result.cacheable) cacheConfig(item, result.object);
            ConfigLoadTask.continueLoad(() -> appendMissingConfig(result));
        });
    }

    private synchronized void appendMissingConfig(ConfigResult result) {
        ConfigLoadTask.check();
        try {
            loadedObjects.put(result.config.getId(), result.object.deepCopy());
            JsonObject added = new JsonObject();
            mergeConfig(added, result.object, result.config);
            initSite(added);
            Map<Integer, Integer> order = new HashMap<>();
            for (int index = 0; index < selectedConfigs.size(); index++) order.put(selectedConfigs.get(index).getId(), index);
            sites.sort(java.util.Comparator.comparingInt(site -> order.getOrDefault(siteCid(site.getKey(), config.getId()), Integer.MAX_VALUE)));
            JsonObject merged = new JsonObject();
            for (Config item : selectedConfigs) {
                JsonObject object = loadedObjects.get(item.getId());
                if (object != null) mergeConfig(merged, object, item);
            }
            // Rebuild parser priority and global rules while retaining existing Site instances.
            initParse(merged);
            initOther(merged);
            preloadJars(added);
            if (loadLive && merged.has("lives") && LiveConfig.get().needSync(result.config.getUrl())) LiveConfig.get().parse(merged);
            ConfigLoadTask.post(RefreshEvent::config);
        } catch (Throwable error) {
            ConfigLoadTask.check();
            ThreadPools.log(error, "Missing config load failed.");
        }
    }

    private ConfigResult readCachedConfig(Config item) {
        ConfigLoadTask.check();
        if (TextUtils.isEmpty(item.getJson())) return null;
        try {
            JsonObject object = Json.parse(item.getJson()).getAsJsonObject();
            ConfigPayloadValidator.vod(object);
            return ConfigResult.success(item, object, false);
        } catch (Throwable error) {
            ConfigLoadTask.check();
            return null;
        }
    }

    private ConfigResult loadConfigResult(Config item) {
        try {
            return ConfigResult.success(item, loadObject(item.getUrl(), 0), true);
        } catch (Throwable e) {
            ConfigLoadTask.check();
            ConfigResult cached = readCachedConfig(item);
            if (cached != null) return cached;
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
        ConfigLoadTask.check();
        ConfigPayloadValidator.vod(object);
        Config.cacheContent(item, object.toString(), ConfigLoadTask.startedAt());
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
        if (!object.has("urls")) {
            ConfigPayloadValidator.vod(object);
            return object;
        }
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
                ConfigLoadTask.check();
                error = e;
                ThreadPools.log(e, "VOD config operation failed.");
            }
        }
        if (error != null) throw error;
        throw new IllegalStateException(ResUtil.getString(R.string.error_config_parse));
    }

    private void mergeConfig(JsonObject target, JsonObject source, Config config) {
        JsonObject root = source.has("video") ? source.getAsJsonObject("video") : source;
        mergeSites(target, root, config);
        LiveConfig.mergeLives(target, source);
        if (root != source) LiveConfig.mergeLives(target, root);
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
        if (hasUsableCachedConfig(config)) {
            setPersistCache(false);
            checkJson(Json.parse(config.getJson()).getAsJsonObject(), callback);
        }
        else ConfigLoadTask.post(() -> callback.error(Notify.getError(R.string.error_config_get, e)));
    }

    private boolean hasUsableCachedConfig(Config item) {
        if (TextUtils.isEmpty(item.getJson())) return false;
        try {
            ConfigPayloadValidator.cachedVod(Json.parse(item.getJson()).getAsJsonObject());
            return true;
        } catch (RuntimeException error) {
            return false;
        }
    }

    private void loadConfigCache(Callback callback) {
        if (config.isEmpty()) config = Config.vod();
        if (hasUsableCachedConfig(config)) {
            Config target = config;
            boolean expired = !config.isCache();
            setPersistCache(false);
            parseConfig(config.getJson(), callback);
            if (expired) ConfigLoadTask.background(() -> {
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
        ConfigLoadTask.check();
        if (object.has("msg") && callback != null) {
            ConfigLoadTask.post(() -> callback.error(object.get("msg").getAsString()));
        } else if (object.has("urls")) {
            parseDepot(object, callback);
        } else {
            parseConfig(object, callback);
        }
    }

    private void parseDepot(JsonObject object, Callback callback) {
        List<Depot> items = Depot.arrayFrom(object.getAsJsonArray("urls").toString());
        if (items.isEmpty()) {
            if (callback != null) ConfigLoadTask.post(() -> callback.error(ResUtil.getString(R.string.error_config_parse)));
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
                ConfigLoadTask.check();
                error = e;
                ThreadPools.log(e, "VOD config operation failed.");
            }
        }
        Throwable cause = error == null ? new Throwable("No valid config") : error;
        if (callback != null) ConfigLoadTask.post(() -> callback.error(Notify.getError(R.string.error_config_get, cause)));
    }

    private JsonObject loadDepotObject(Config target) throws Throwable {
        if (hasUsableCachedConfig(target)) {
            setPersistCache(false);
            if (!target.isCache()) ConfigLoadTask.background(() -> refreshDepotCache(target));
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
            ConfigLoadTask.check();
            ThreadPools.log(e, "VOD config operation failed.");
            if (callback != null) ConfigLoadTask.post(() -> callback.error(Notify.getError(R.string.error_config_parse, e)));
        }
    }

    private synchronized void parseConfig(JsonObject object, Callback callback) {
        try {
            ConfigLoadTask.check();
            if (object.has("urls")) {
                parseDepot(object, callback);
                return;
            }
            ConfigPayloadValidator.vod(object);
            initSite(object);
            initParse(object);
            initOther(object);
            preloadJars(object);
            if (loadLive && object.has("lives")) initLive(object);
            String notice = Json.safeString(object, "notice");
            config.logo(Json.safeString(object, "logo"));
            if (persistCache) config.json(object.toString()).update();
            ConfigLoadTask.check();
            if (selection != null) selection.commit(config);
            config.activate();
            postSuccess(callback, notice);
        } catch (Throwable e) {
            ConfigLoadTask.check();
            ThreadPools.log(e, "VOD config operation failed.");
            if (callback != null) ConfigLoadTask.post(() -> callback.error(Notify.getError(R.string.error_config_parse, e)));
        }
    }

    private void postSuccess(Callback callback, String notice) {
        if (callback == null) return;
        if (TextUtils.isEmpty(notice)) ConfigLoadTask.post(callback::success);
        else ConfigLoadTask.post(() -> callback.success(notice));
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
            addSite(site, spider, cache);
        }
        for (Site site : sites) {
            if (site.getKey().equals(config.getHome()) || site.getKey().equals(siteKey(config.getId(), config.getHome()))) {
                setHome(site);
            }
        }
    }

    void addSite(Site site, String spider, Map<String, Site> cache) {
        if (!site.isEmpty() && !isScopedSiteKey(site.getKey())) site.setKey(siteKey(config.getId(), site.getKey()));
        if (siteMap.containsKey(site.getKey())) return;
        site.setJar(parseJar(site, spider));
        sites.add(site.trans().sync(cache));
        siteMap.put(site.getKey(), site);
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
            LiveConfig.get().reset().config(temp).parse(object);
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
        List<Parse> entries = new ArrayList<>();
        for (JsonElement element : Json.safeListElement(object, "parses")) {
            entries.add(Parse.objectFrom(element));
        }
        setParses(entries, Parse.god());
    }

    void setParses(List<Parse> entries, Parse god) {
        boolean retainSelection = parse != null;
        String selected = retainSelection ? parse.getName() : config.getParse();
        Map<String, Parse> ordered = new LinkedHashMap<>();
        for (Parse parse : entries) {
            ordered.putIfAbsent(parse.getName(), parse);
        }
        List<Parse> updated = new ArrayList<>(ordered.values());
        if (!updated.isEmpty() && !ordered.containsKey(god.getName())) {
            updated.add(0, god);
            ordered.put(god.getName(), god);
        }
        parses = updated;
        parseMap = ordered;
        Parse restored = ordered.get(selected);
        if (restored != null && (retainSelection || restored.getType() > 1)) setParse(restored);
        else if (!updated.isEmpty()) setParse(updated.get(0));
        else parse = null;
    }

    private void initOther(JsonObject object) {
        if ((home == null || home.isEmpty()) && !sites.isEmpty()) setHome(sites.get(0));
        if ((parse == null || parse.isEmpty()) && !parses.isEmpty()) setParse(parses.get(0));
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

    public static void refreshNetwork() {
        OkHttp.clearConfig();
        OkHttp.get().setProxy(Setting.getProxy());
        OkHttp.get().setDoh(Doh.objectFrom(Setting.getDoh()));
        OkHttp.responseInterceptor().addAll(get().getHeaders());
        OkHttp.responseInterceptor().addAll(LiveConfig.get().getHeaders());
        OkHttp.authenticator().addAll(get().getProxy());
        OkHttp.authenticator().addAll(LiveConfig.get().getProxy());
        OkHttp.selector().addProxyAll(get().getProxy());
        OkHttp.selector().addProxyAll(LiveConfig.get().getProxy());
        OkHttp.resolver().addAll(get().getHosts());
        OkHttp.resolver().addAll(LiveConfig.get().getHosts());
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
        return new ArrayList<>(sites);
    }

    public List<Parse> getParses() {
        return new ArrayList<>(parses);
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
        this.flags = new ArrayList<>(flags);
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
        Parse current = parseMap.get(parse.getName());
        this.parse = current == null ? parse : current;
        this.parse.setActivated(true);
        config.saveParse(this.parse.getName());
        for (Parse item : getParses()) item.setActivated(this.parse);
    }

    public void setHome(Site home) {
        this.home = home;
        this.home.setActivated(true);
        config.saveHome(isScopedSiteKey(home.getKey()) && siteCid(home.getKey(), config.getId()) != config.getId() ? home.getKey() : rawSiteKey(home.getKey()));
        for (Site item : getSites()) item.setActivated(home);
        ConfigLoadTask.background(() -> {
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
