package com.fongmi.android.tv.api.config;

import android.text.TextUtils;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.Setting;
import com.fongmi.android.tv.api.Decoder;
import com.fongmi.android.tv.api.LiveParser;
import com.fongmi.android.tv.api.loader.BaseLoader;
import com.fongmi.android.tv.bean.Channel;
import com.fongmi.android.tv.bean.Config;
import com.fongmi.android.tv.repository.ConfigRepository;
import com.fongmi.android.tv.bean.Depot;
import com.fongmi.android.tv.bean.Group;
import com.fongmi.android.tv.bean.Keep;
import com.fongmi.android.tv.repository.KeepRepository;
import com.fongmi.android.tv.bean.Live;
import com.fongmi.android.tv.bean.Rule;
import com.fongmi.android.tv.db.AppDatabase;
import com.fongmi.android.tv.repository.LiveRepository;
import com.fongmi.android.tv.impl.Callback;
import com.fongmi.android.tv.event.RefreshEvent;
import com.fongmi.android.tv.ui.activity.LiveActivity;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.OrderedParallelMapper;
import com.fongmi.android.tv.utils.CacheFirstLoader;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.ThreadPools;
import com.fongmi.android.tv.utils.UrlUtil;
import com.github.catvod.bean.Header;
import com.github.catvod.bean.Proxy;
import com.github.catvod.utils.Json;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class LiveConfig {

    private final List<Live> lives = new java.util.concurrent.CopyOnWriteArrayList<>();
    private final Map<String, Live> liveMap = new java.util.concurrent.ConcurrentHashMap<>();
    private List<Rule> rules = new ArrayList<>();
    private List<String> ads = new ArrayList<>();
    private List<Header> headers = new ArrayList<>();
    private List<Proxy> proxy = new ArrayList<>();
    private List<String> hosts = new ArrayList<>();
    private List<String> ruleHosts = new ArrayList<>();
    private boolean persistCache = true;
    private final Map<Integer, JsonObject> loadedObjects = new HashMap<>();
    private List<Config> selectedConfigs = new ArrayList<>();
    private final LiveConfigLoader documents = new LiveConfigLoader();
    private Config config = new Config();
    private ConfigDocument document;
    private String pendingContent;
    public ConfigDocument document() { return document; }
    private boolean sync;
    private boolean requestBoot;
    private Live home;

    static void publish(LiveConfig state) { ConfigRuntime.publishLive(state); }

    public static LiveConfig get() {
        return ConfigRuntime.snapshot().live;
    }

    public static String getUrl() {
        return get().getConfig().getUrl();
    }

    public static String getDesc() {
        return get().getConfig().getDesc();
    }

    public static String getResp() {
        return get().getHome().getCore().getResp();
    }

    public static int getHomeIndex() {
        return get().getLives().indexOf(get().getHome());
    }

    public static boolean isOnly() {
        return get().getLives().size() == 1;
    }

    public static boolean isEmpty() {
        return get().getHome().isEmpty();
    }

    public static boolean hasUrl() {
        return getUrl() != null && getUrl().length() > 0;
    }

    public static void load(Config config, Callback callback) {
        Config requested = config == null ? new Config() : config.copy();
        ConfigRequests.live(draft -> draft.config(requested).loadConfig(callback));
    }

    public static void loadCached(Config config, Callback callback) {
        Config requested = config == null ? new Config() : config.copy();
        ConfigRequests.live(draft -> {
            LiveConfig target = draft.config(requested);
            target.loadConfigCache(callback);
        });
    }

    public static void load(List<Config> input, Callback callback) {
        if (input == null || input.isEmpty()) return;
        List<Config> configs = ConfigRequests.capture(input);
        ConfigRequests.live(draft -> draft.config(configs.get(0)).setPersistCache(false).loadConfigs(configs, callback));
    }

    public static void loadCached(List<Config> input, Callback callback) {
        if (input == null || input.isEmpty()) return;
        List<Config> configs = ConfigRequests.capture(input);
        ConfigRequests.live(draft -> draft.config(configs.get(0)).setPersistCache(false).loadConfigsCache(configs, callback));
    }

    public static void reload(Callback callback) {
        ConfigRequests.live(draft -> {
            List<Config> configs = ConfigRepository.get().selected(1);
            LiveConfig target = draft.config(configs.get(0));
            if (configs.size() == 1) target.loadConfig(callback);
            else target.setPersistCache(false).loadConfigs(configs, callback);
        });
    }

    /** Clears shared state after already queued configuration work has finished. */
    public static void release() {
        ConfigRequests.live(ConfigApplicationService::live);
    }

    private LiveConfig setPersistCache(boolean persistCache) {
        this.persistCache = persistCache;
        return this;
    }

    synchronized LiveConfig reset() {
        clear();
        this.persistCache = true;
        return config(new Config());
    }

    public synchronized LiveConfig config(Config config) {
        this.config = config == null ? new Config() : config.copy();
        config = this.config;
        if (config.getUrl() == null) return this;
        this.sync = config.getUrl().equals(VodConfig.getUrl());
        return this;
    }

    public synchronized LiveConfig clear() {
        this.home = null;
        this.pendingContent = null;
        this.document = null;
        this.requestBoot = false;
        this.loadedObjects.clear();
        this.selectedConfigs.clear();
        this.ads.clear();
        this.hosts.clear();
        this.proxy.clear();
        this.rules.clear();
        this.headers.clear();
        this.ruleHosts.clear();
        this.lives.clear();
        this.liveMap.clear();
        return this;
    }

    private void loadConfig(Callback callback) {
        try {
            if (config.isEmpty()) config(Config.live());
            commitBuild(() -> parseConfigOrThrow(Decoder.getJson(config.getUrl())));
            if (callback != null) ConfigLoadTask.post(callback::success);
        } catch (Throwable e) {
            ConfigLoadTask.check();
            if (config.isEmpty() || TextUtils.isEmpty(config.getUrl())) ConfigLoadTask.post(() -> callback.error(""));
            else loadCache(callback, e);
            ThreadPools.log(e, "Live config operation failed.");
        }
    }

    private void loadConfigs(List<Config> configs, Callback callback) {
        loadConfigs(configs, callback, false);
    }

    private void loadConfigsCache(List<Config> configs, Callback callback) {
        loadConfigs(configs, callback, true);
    }

    private void loadConfigs(List<Config> configs, Callback callback, boolean cache) {
        selectedConfigs = new ArrayList<>(configs);
        JsonObject merged = new JsonObject();
        Throwable error = null;
        int success = 0;
        for (LiveConfigLoader.Result result : loadConfigResults(configs, cache)) {
            try {
                if (result.error != null) throw result.error;
                if (result.live != null) {
                    addLive(result.live);
                    success++;
                    continue;
                }
                if (result.cacheable) cacheConfig(result.config, result.object);
                loadedObjects.put(result.config.getId(), result.object.deepCopy());
                mergeConfig(merged, result.object);
                success++;
            } catch (Throwable e) {
                ConfigLoadTask.check();
                error = e;
                ThreadPools.log(e, "Live config operation failed.");
            }
        }
        ConfigLoadTask.check();
        if (success == 0) {
            Throwable cause = error == null ? new Throwable("No valid config") : error;
            if (callback != null) ConfigLoadTask.post(() -> callback.error(Notify.getError(R.string.error_config_get, cause)));
            return;
        }
        try {
            commitBuild(() -> {
                if (!merged.entrySet().isEmpty()) parseConfigOrThrow(merged);
            });
            if (callback != null) ConfigLoadTask.post(callback::success);
        } catch (Throwable failure) {
            ConfigLoadTask.check();
            ThreadPools.log(failure, "Live config activation failed.");
            if (callback != null) ConfigLoadTask.post(() -> callback.error(Notify.getError(R.string.error_config_parse, failure)));
        }
    }


    private synchronized void addLive(Live live) {
        if (liveMap.containsKey(live.getName())) return;
        lives.add(live);
        liveMap.put(live.getName(), live);
        if (home == null) restoreHome(live);
    }

    private boolean hasUsableCachedConfig(Config item) {
        if (TextUtils.isEmpty(item.getJson())) return false;
        try {
            if (Json.invalid(item.getJson())) validateText(item.getUrl(), item.getJson());
            else ConfigPayloadValidator.cachedLive(Json.parse(item.getJson()).getAsJsonObject());
            return true;
        } catch (RuntimeException error) {
            return false;
        }
    }

    private void loadConfigCache(Callback callback) {
        if (config.isEmpty()) config(Config.live());
        if (hasUsableCachedConfig(config)) {
            Config target = config;
            boolean expired = !config.isCache();
            setPersistCache(false);
            parseConfig(config.getJson(), callback);
            if (expired) ConfigLoadTask.background(() -> {
                try {
                    String text = Decoder.getJson(target.getUrl());
                    if (Json.invalid(text)) {
                        cacheConfig(target, text);
                    } else {
                        JsonObject object = loadObject(Json.parse(text).getAsJsonObject(), 0);
                        cacheConfig(target, object);
                    }
                } catch (Throwable ignored) {
                }
            });
        } else {
            loadConfig(callback);
        }
    }

    private void loadCache(Callback callback, Throwable e) {
        if (hasUsableCachedConfig(config)) {
            setPersistCache(false);
            parseConfig(config.getJson(), callback);
        }
        else ConfigLoadTask.post(() -> callback.error(Notify.getError(R.string.error_config_get, e)));
    }

    private List<LiveConfigLoader.Result> loadConfigResults(List<Config> configs, boolean cache) {
        List<Config> unique = getUniqueConfigs(configs);
        if (unique.isEmpty()) return Collections.emptyList();
        if (cache) return CacheFirstLoader.load(unique, this::readCachedConfig,
                item -> !item.isCache(), item -> ConfigLoadTask.background(() -> refreshDepotCache(item)),
                this::fetchMissingConfig, () -> loadConfigResults(unique, false));
        try {
            return OrderedParallelMapper.map(ThreadPools.config(), unique, ConfigLoadTask.bind(this::loadConfigResult), error -> ThreadPools.log(error, "Live config operation failed."));
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            return Collections.emptyList();
        }
    }

    private void fetchMissingConfig(Config item) {
        ConfigLoadTask.background(() -> {
            LiveConfigLoader.Result result = loadConfigResult(item);
            ConfigLoadTask.check();
            if (result.error != null) {
                ThreadPools.log(result.error, "Missing config fetch failed.");
                return;
            }
            if (result.cacheable) cacheConfig(item, result.object);
            ConfigLoadTask.continueLoad(() -> appendMissingConfig(result));
        });
    }

    private synchronized LiveConfig forkForAppend() {
        LiveConfig copy = new LiveConfig();
        copy.config = config.copy(); copy.home = home; copy.persistCache = false;
        copy.selectedConfigs = new ArrayList<>(selectedConfigs);
        for (Map.Entry<Integer, JsonObject> entry : loadedObjects.entrySet()) copy.loadedObjects.put(entry.getKey(), entry.getValue().deepCopy());
        copy.rules = new ArrayList<>(rules);
        copy.ads = new ArrayList<>(ads);
        copy.headers = new ArrayList<>(headers);
        copy.proxy = new ArrayList<>(proxy);
        copy.hosts = new ArrayList<>(hosts);
        copy.ruleHosts = new ArrayList<>(ruleHosts);
        copy.lives.addAll(lives); copy.liveMap.putAll(liveMap); copy.sync = sync; copy.document = document;
        return copy;
    }

    private void appendMissingConfig(LiveConfigLoader.Result result) {
        ConfigLoadTask.commit(() -> get().forkForAppend().appendResult(result));
    }

    private synchronized void appendResult(LiveConfigLoader.Result result) {
        ConfigLoadTask.check();
        try {
            commitBuild(() -> {
                if (result.live != null) addLive(result.live);
                else {
                    loadedObjects.put(result.config.getId(), result.object.deepCopy());
                    JsonObject merged = new JsonObject();
                    for (Config item : selectedConfigs) {
                        JsonObject object = loadedObjects.get(item.getId());
                        if (object != null) mergeConfig(merged, object);
                    }
                    parseConfigOrThrow(merged);
                }
            });
            ConfigLoadTask.post(RefreshEvent::config);
        } catch (Throwable error) {
            ConfigLoadTask.check();
            ThreadPools.log(error, "Missing config load failed.");
        }
    }

    private LiveConfigLoader.Result readCachedConfig(Config item) { return documents.readCachedConfig(item); }

    private LiveConfigLoader.Result loadConfigResult(Config item) { return documents.loadResult(item); }

    private List<Config> getUniqueConfigs(List<Config> configs) { return documents.getUniqueConfigs(configs); }

    private String normalizeConfigUrl(String url) { return documents.normalizeConfigUrl(url); }

    private void cacheConfig(Config item, JsonObject object) { documents.cacheConfig(item, object); }

    private void cacheConfig(Config item, String text) { documents.cacheConfig(item, text); }

    private JsonObject loadObject(JsonObject object, int depth) throws Throwable { return documents.loadObject(object, depth); }



    private void mergeConfig(JsonObject target, JsonObject source) { ConfigMerger.live(target,source); }

    static void mergeLives(JsonObject target, JsonObject source) { ConfigMerger.lives(target,source); }

    private void appendArray(JsonObject target, JsonObject source, String key) {
        if (!source.has(key) || source.get(key).isJsonNull()) return;
        if (!target.has(key)) target.add(key, new JsonArray());
        JsonArray targetArray = target.getAsJsonArray(key);
        for (JsonElement element : source.getAsJsonArray(key)) targetArray.add(element.deepCopy());
    }

    private void parseConfig(String text, Callback callback) {
        try {
            commitBuild(() -> parseConfigOrThrow(text));
            if (callback != null) ConfigLoadTask.post(callback::success);
        } catch (Throwable e) {
            ConfigLoadTask.check();
            ThreadPools.log(e, "Live config operation failed.");
            if (callback != null) ConfigLoadTask.post(() -> callback.error(Notify.getError(R.string.error_config_parse, e)));
        }
    }

    private void parseText(String text, Callback callback) {
        try {
            commitBuild(() -> parseTextOrThrow(text));
            if (callback != null) ConfigLoadTask.post(callback::success);
        } catch (Throwable e) {
            ConfigLoadTask.check();
            ThreadPools.log(e, "Live config operation failed.");
            if (callback != null) ConfigLoadTask.post(() -> callback.error(Notify.getError(R.string.error_config_parse, e)));
        }
    }

    private synchronized void parseText(String url, String text) {
        Live live = new Live(parseName(url), url).sync();
        LiveParser.text(live, text);
        if (liveMap.containsKey(live.getName())) return;
        lives.add(live);
        liveMap.put(live.getName(), live);
        if (home == null) restoreHome(live);
    }

    private String parseName(String url) { return documents.parseName(url); }

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
        try {
            commitBuild(() -> parseDepotOrThrow(object));
            if (callback != null) ConfigLoadTask.post(callback::success);
        } catch (Throwable e) {
            ConfigLoadTask.check();
            ThreadPools.log(e, "Live config operation failed.");
            if (callback != null) ConfigLoadTask.post(() -> callback.error(Notify.getError(R.string.error_config_get, e)));
        }
    }

    private void parseDepotOrThrow(JsonObject object) throws Throwable {
        List<Depot> items = Depot.arrayFrom(object.getAsJsonArray("urls").toString());
        if (items.isEmpty()) throw new IllegalStateException(ResUtil.getString(R.string.error_config_parse));
        ConfigRepository.get().delete(config.getUrl());
        Throwable error = null;
        for (Depot item : items) {
            try {
                Config target = ConfigRepository.get().find(item, 1);
                String text = loadDepotConfig(target);
                clear();
                config(target);
                parseConfigOrThrow(text);
                return;
            } catch (Throwable e) {
                ConfigLoadTask.check();
                error = e;
                ThreadPools.log(e, "Live config operation failed.");
            }
        }
        if (error != null) throw error;
        throw new IllegalStateException("No valid config");
    }

    private String loadDepotConfig(Config target) throws Throwable {
        if (hasUsableCachedConfig(target)) {
            setPersistCache(false);
            if (!target.isCache()) ConfigLoadTask.background(() -> refreshDepotCache(target));
            return target.getJson();
        }
        String text = Decoder.getJson(target.getUrl());
        if (Json.invalid(text)) {
            cacheConfig(target, text);
            return text;
        }
        JsonObject loaded = loadObject(Json.parse(text).getAsJsonObject(), 0);
        cacheConfig(target, loaded);
        return loaded.toString();
    }

    private void refreshDepotCache(Config target) { documents.refreshDepotCache(target); }

    private synchronized void parseConfig(JsonObject object, Callback callback) {
        try {
            ConfigLoadTask.check();
            commitBuild(() -> parseConfigOrThrow(object));
            if (callback != null) ConfigLoadTask.post(callback::success);
        } catch (Throwable e) {
            ConfigLoadTask.check();
            ThreadPools.log(e, "Live config operation failed.");
            if (callback != null) ConfigLoadTask.post(() -> callback.error(Notify.getError(R.string.error_config_parse, e)));
        }
    }

    private void parseConfigOrThrow(String text) throws Throwable {
        ConfigLoadTask.check();
        if (Json.invalid(text)) parseTextOrThrow(text);
        else {
            JsonObject object = Json.parse(text).getAsJsonObject();
            if (object.has("msg")) throw new IllegalStateException(object.get("msg").getAsString());
            if (object.has("urls")) parseDepotOrThrow(object);
            else parseConfigOrThrow(object);
        }
    }

    private void validateText(String url, String text) { documents.validateText(url, text); }

    private void requireChannels(Live live) { documents.requireChannels(live); }

    private void parseTextOrThrow(String text) throws Throwable {
        ConfigLoadTask.check();
        Live live = new Live(parseName(config.getUrl()), config.getUrl()).sync();
        LiveParser.text(live, text);
        requireChannels(live);
        lives.add(live);
        liveMap.put(live.getName(), live);
        restoreHome(live);
        pendingContent = text;
    }

    private void parseConfigOrThrow(JsonObject object) throws Throwable {
        ConfigLoadTask.check();
        ConfigPayloadValidator.live(object);
        initLive(object);
        initOther(object);
        document = new ConfigDocument(object, Collections.singletonList(config.getUrl()));
        pendingContent = object.toString();
    }

    private void initLive(JsonObject object) {
        String spider = Json.safeString(object, "spider");
        Map<String, Live> cache = new HashMap<>();
        for (Live live : LiveRepository.get().all()) cache.put(live.getName(), live);
        for (JsonElement element : liveEntries(object)) {
            Live live = Live.objectFrom(element, spider);
            if (liveMap.containsKey(live.getName())) {
                Live old = liveMap.get(live.getName());
                if (old != null) {
                    updateLive(old, live);
                }
                continue;
            }
            live.setJar(parseJar(live, spider));
            lives.add(live.sync(cache));
            liveMap.put(live.getName(), live);
        }
        for (Live live : lives) {
            if (live.getName().equals(config.getHome())) {
                restoreHome(live);
            }
        }
    }

    static void updateLive(Live old, Live live) {
        if (!old.getUrl().equals(live.getUrl()) || !old.getApi().equals(live.getApi())
                || !old.getExt().equals(live.getExt()) || !old.getJar().equals(live.getJar())) old.getGroups().clear();
        old.setUrl(live.getUrl());
        old.setApi(live.getApi());
        old.setExt(live.getExt());
        old.setJar(live.getJar());
        old.updateMetadata(live);
    }

    static List<JsonElement> liveEntries(JsonObject object) {
        List<JsonElement> entries = new ArrayList<>();
        Set<String> names = new HashSet<>();
        for (JsonElement element : Json.safeListElement(object, "lives")) {
            JsonElement name = element.getAsJsonObject().get("name");
            String key = name == null || name.isJsonNull() ? "" : name.getAsString();
            if (names.add(key)) entries.add(element);
        }
        return entries;
    }

    void preloadJars() {
        Set<String> jars = new LinkedHashSet<>();
        if (document != null) jars.add(Json.safeString(document.object(), "spider"));
        for (Live live : lives) jars.add(live.getJar());
        for (String jar : jars) BaseLoader.get().parseJar(jar);
    }

    void persistSelection() {
        if (pendingContent != null && persistCache) config.json(pendingContent).update();
        if (pendingContent != null || !lives.isEmpty()) {
            config.saveHome(config.getHome()); config.activate();
        }
        pendingContent = null;
    }

    void retireReplacedBy(LiveConfig next) {
        for (Live live : getLives()) {
            Live retained = next.liveMap.get(live.getName());
            if (retained == live) continue;
            BaseLoader.get().clearLive(live.getName(), live.getApi(), live.getExt(), live.getJar());
        }
    }

    private void initOther(JsonObject object) {
        if (home == null) restoreHome(lives.isEmpty() ? new Live() : lives.get(0));
        setHeaders(Header.arrayFrom(object.get("headers")));
        setProxy(Proxy.arrayFrom(object.get("proxy")));
        setHosts(Json.safeListString(object, "hosts"));
        setRules(Rule.arrayFrom(object.get("rules")));
        setAds(Json.safeListString(object, "ads"));
    }

    private String parseApi(String api) {
        if (api.startsWith("file") || api.startsWith("assets")) return UrlUtil.convert(api);
        return api;
    }

    private String parseExt(String ext) {
        if (ext.startsWith("file") || ext.startsWith("assets")) return UrlUtil.convert(ext);
        if (ext.startsWith("img+")) return Decoder.getExt(ext);
        return ext;
    }

    private String parseJar(Live live, String spider) {
        if (live.getJar().isEmpty() && live.getApi().startsWith("csp_")) return spider;
        return live.getJar();
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

    private void bootLive() {
        Setting.putBootLive(false);
        LiveActivity.start(App.get());
    }

    public void parse(JsonObject object) {
        // A VOD merge is an active view, not the payload of this live URL.
        LiveConfig candidate = new LiveConfig().config(config).setPersistCache(false);
        candidate.parseConfig(object, null);
    }

    static LiveConfig inherit(Config owner, JsonObject object) throws Throwable {
        LiveConfig candidate = new LiveConfig().config(owner).setPersistCache(false);
        candidate.sync = true;
        candidate.parseConfigOrThrow(object);
        return candidate;
    }

    public void setKeep(Channel channel) {
        if (home == null || channel.getGroup().isHidden() || channel.getUrls().isEmpty()) return;
        Setting.putKeep(home.getName() + AppDatabase.SYMBOL + channel.getGroup().getName() + AppDatabase.SYMBOL + channel.getName() + AppDatabase.SYMBOL + channel.getCurrent());
    }

    public void setKeep(List<Group> items) {
        Set<String> keys = new HashSet<>();
        for (Keep keep : KeepRepository.get().live()) keys.add(keep.getKey());
        for (Group group : items) {
            if (group.isKeep()) continue;
            for (Channel channel : group.getChannel()) {
                if (keys.contains(channel.getName())) {
                    items.get(0).add(channel);
                }
            }
        }
    }

    public int[] find(List<Group> items) {
        String[] splits = Setting.getKeep().split(AppDatabase.SYMBOL);
        if (items.isEmpty()) return new int[]{-1, -1};
        if (splits.length < 4 || !getHome().getName().equals(splits[0])) return new int[]{0, 0};
        for (int i = 0; i < items.size(); i++) {
            Group group = items.get(i);
            if (group.getName().equals(splits[1])) {
                int j = group.find(splits[2]);
                if (j != -1 && splits.length == 4) group.getChannel().get(j).setLine(splits[3]);
                if (j != -1) return new int[]{i, j};
            }
        }
        return new int[]{0, 0};
    }

    public int[] find(String number, List<Group> items) {
        int target;
        try {
            target = Integer.parseInt(number);
        } catch (Exception e) {
            return new int[]{-1, -1};
        }
        for (int i = 0; i < items.size(); i++) {
            int j = items.get(i).find(target);
            if (j != -1) return new int[]{i, j};
        }
        return new int[]{-1, -1};
    }

    public boolean needSync(String url) {
        Config config = getConfig();
        return sync || TextUtils.isEmpty(config.getUrl()) || url.equals(config.getUrl());
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

    public List<String> getAds() {
        return ads == null ? Collections.emptyList() : ads;
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

    private void setAds(List<String> ads) {
        this.ads = ads;
    }

    public List<Live> getLives() {
        return new ArrayList<>(lives);
    }

    public Config getConfig() {
        return config == null ? Config.live() : config;
    }

    public Live getHome() {
        return home == null ? new Live() : home;
    }

    public Live getLive(String key) {
        Live live = liveMap.get(key);
        return live == null ? new Live() : live;
    }

    public void setHome(Live home) {
        restoreHome(home);
        requestBoot = false;
        config.saveHome(home.getName());
    }

    private void restoreHome(Live home) {
        this.home = home;
        this.home.setActivated(true);
        config.setHome(home.getName());
        for (Live item : getLives()) item.setActivated(home);
        requestBoot = true;
    }

    void afterPublication() {
        if (!requestBoot) return;
        requestBoot = false;
        if (App.activity() instanceof LiveActivity) return;
        if (home != null && (home.isBoot() || Setting.isBootLive())) ConfigLoadTask.post(this::bootLive);
    }

    private interface Build { void run() throws Throwable; }
    private void commitBuild(Build build) throws Throwable {
        ConfigLoadTransaction.execute(() -> { build.run(); return this; }, ConfigApplicationService::live);
    }
}
