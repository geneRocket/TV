package com.fongmi.android.tv.api.config;

import android.net.Uri;
import android.text.TextUtils;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.Setting;
import com.fongmi.android.tv.api.Decoder;
import com.fongmi.android.tv.api.LiveParser;
import com.fongmi.android.tv.api.loader.BaseLoader;
import com.fongmi.android.tv.bean.Channel;
import com.fongmi.android.tv.bean.Config;
import com.fongmi.android.tv.bean.Depot;
import com.fongmi.android.tv.bean.Group;
import com.fongmi.android.tv.bean.Keep;
import com.fongmi.android.tv.bean.Live;
import com.fongmi.android.tv.bean.Rule;
import com.fongmi.android.tv.db.AppDatabase;
import com.fongmi.android.tv.impl.Callback;
import com.fongmi.android.tv.ui.activity.LiveActivity;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.ThreadPools;
import com.fongmi.android.tv.utils.UrlUtil;
import com.github.catvod.bean.Header;
import com.github.catvod.bean.Proxy;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Json;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

public class LiveConfig {

    private List<Live> lives;
    private Map<String, Live> liveMap;
    private List<Rule> rules;
    private List<String> ads;
    private List<Header> headers;
    private List<Proxy> proxy;
    private List<String> hosts;
    private List<String> ruleHosts;
    private boolean persistCache;
    private Config config;
    private boolean sync;
    private Live home;

    private static class Loader {
        static volatile LiveConfig INSTANCE = new LiveConfig();
    }

    public static LiveConfig get() {
        return Loader.INSTANCE;
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
        get().init().clear().config(config).load(callback);
    }

    public static void load(Config config, Callback callback, boolean cache) {
        get().init().clear().config(config).load(callback, cache);
    }

    public static void load(List<Config> configs, Callback callback) {
        if (configs == null || configs.isEmpty()) return;
        get().init().clear().config(configs.get(0)).loadMulti(configs, callback);
    }

    public static void load(List<Config> configs, Callback callback, boolean cache) {
        if (configs == null || configs.isEmpty()) return;
        get().init().clear().config(configs.get(0)).loadMulti(configs, callback, cache);
    }

    public synchronized LiveConfig init() {
        this.home = null;
        if (this.ads == null) this.ads = new ArrayList<>();
        if (this.hosts == null) this.hosts = new ArrayList<>();
        if (this.proxy == null) this.proxy = new ArrayList<>();
        if (this.rules == null) this.rules = new ArrayList<>();
        if (this.headers == null) this.headers = new ArrayList<>();
        if (this.ruleHosts == null) this.ruleHosts = new ArrayList<>();
        if (this.lives == null) this.lives = new ArrayList<>();
        if (this.liveMap == null) this.liveMap = new HashMap<>();
        this.persistCache = true;
        return config(new Config());
    }

    public synchronized LiveConfig config(Config config) {
        this.config = config;
        if (config.getUrl() == null) return this;
        this.sync = config.getUrl().equals(VodConfig.getUrl());
        return this;
    }

    public synchronized LiveConfig clear() {
        for (Live live : getLives()) BaseLoader.get().clearLive(live.getName(), live.getApi(), live.getExt(), live.getJar());
        this.home = null;
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

    public void load() {
        if (isEmpty()) load(new Callback());
    }

    public void load(Callback callback) {
        ThreadPools.config().execute(() -> loadConfig(callback));
    }

    public void load(Callback callback, boolean cache) {
        if (cache) ThreadPools.config().execute(() -> loadConfigCache(callback));
        else ThreadPools.config().execute(() -> loadConfig(callback));
    }

    public void loadMulti(List<Config> configs, Callback callback) {
        this.persistCache = false;
        ThreadPools.config().execute(() -> loadConfigs(configs, callback));
    }

    public void loadMulti(List<Config> configs, Callback callback, boolean cache) {
        this.persistCache = false;
        if (cache) ThreadPools.config().execute(() -> loadConfigsCache(configs, callback));
        else ThreadPools.config().execute(() -> loadConfigs(configs, callback));
    }

    private void loadConfig(Callback callback) {
        try {
            if (config.isEmpty()) config(Config.live());
            parseConfig(Decoder.getJson(config.getUrl()), callback);
        } catch (Throwable e) {
            if (config.isEmpty() || TextUtils.isEmpty(config.getUrl())) App.post(() -> callback.error(""));
            else loadCache(callback, e);
            e.printStackTrace();
        }
    }

    private void loadConfigs(List<Config> configs, Callback callback) {
        JsonObject merged = new JsonObject();
        Throwable error = null;
        int success = 0;
        for (ConfigResult result : loadConfigResults(configs, false)) {
            try {
                if (result.error != null) throw result.error;
                if (result.live != null) {
                    addLive(result.live);
                    success++;
                    continue;
                }
                if (result.text != null) {
                    parseText(result.config.getUrl(), result.text);
                    success++;
                    continue;
                }
                if (result.cacheable) cacheConfig(result.config, result.object);
                mergeConfig(merged, result.object);
                success++;
            } catch (Throwable e) {
                error = e;
                e.printStackTrace();
            }
        }
        if (merged.has("lives") || merged.has("headers") || merged.has("proxy") || merged.has("hosts") || merged.has("rules") || merged.has("ads")) parseConfig(merged, null);
        if (success > 0) App.post(callback::success);
        else {
            Throwable cause = error == null ? new Throwable("No valid config") : error;
            App.post(() -> callback.error(Notify.getError(R.string.error_config_get, cause)));
        }
    }

    private synchronized void addLive(Live live) {
        if (liveMap.containsKey(live.getName())) return;
        lives.add(live);
        liveMap.put(live.getName(), live);
        if (home == null) setHome(live, true);
    }

    private void loadConfigCache(Callback callback) {
        if (config.isEmpty()) config(Config.live());
        if (!TextUtils.isEmpty(config.getJson())) {
            Config target = config;
            parseConfig(config.getJson(), callback);
            if (!config.isCache()) ThreadPools.config().execute(() -> {
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
        if (!TextUtils.isEmpty(config.getJson())) checkJson(Json.parse(config.getJson()).getAsJsonObject(), callback);
        else App.post(() -> callback.error(Notify.getError(R.string.error_config_get, e)));
    }

    private void loadConfigsCache(List<Config> configs, Callback callback) {
        JsonObject merged = new JsonObject();
        Throwable error = null;
        int success = 0;
        for (ConfigResult result : loadConfigResults(configs, true)) {
            try {
                if (result.error != null) throw result.error;
                if (result.text != null) {
                    parseText(result.config.getUrl(), result.text);
                    success++;
                    continue;
                }
                if (result.cacheable) cacheConfig(result.config, result.object);
                mergeConfig(merged, result.object);
                success++;
            } catch (Throwable e) {
                error = e;
                e.printStackTrace();
            }
        }
        if (merged.has("lives") || merged.has("headers") || merged.has("proxy") || merged.has("hosts") || merged.has("rules") || merged.has("ads")) parseConfig(merged, null);
        if (success > 0) App.post(callback::success);
        else {
            Throwable cause = error == null ? new Throwable("No valid config") : error;
            App.post(() -> callback.error(Notify.getError(R.string.error_config_get, cause)));
        }
    }

    private List<ConfigResult> loadConfigResults(List<Config> configs, boolean cache) {
        List<Config> unique = getUniqueConfigs(configs);
        if (unique.isEmpty()) return Collections.emptyList();
        ExecutorService executor = ThreadPools.newFixed("live-config", Math.min(unique.size(), com.fongmi.android.tv.Constant.THREAD_POOL));
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
                if (!item.isCache()) ThreadPools.config().execute(() -> {
                    try {
                        String text = Decoder.getJson(item.getUrl());
                        if (Json.invalid(text)) cacheConfig(item, text);
                        else cacheConfig(item, loadObject(Json.parse(text).getAsJsonObject(), 0));
                    } catch (Throwable ignored) {
                    }
                });
                if (Json.invalid(item.getJson())) {
                    Live live = new Live(parseName(item.getUrl()), item.getUrl()).sync();
                    LiveParser.text(live, item.getJson());
                    return ConfigResult.live(item, live);
                }
                return ConfigResult.json(item, Json.parse(item.getJson()).getAsJsonObject(), false);
            }
            String text = Decoder.getJson(item.getUrl());
            if (Json.invalid(text)) {
                Live live = new Live(parseName(item.getUrl()), item.getUrl()).sync();
                LiveParser.text(live, text);
                return ConfigResult.live(item, live);
            }
            return ConfigResult.json(item, loadObject(Json.parse(text).getAsJsonObject(), 0), true);
        } catch (Throwable e) {
            if (!TextUtils.isEmpty(item.getJson())) {
                try {
                    if (Json.invalid(item.getJson())) {
                        Live live = new Live(parseName(item.getUrl()), item.getUrl()).sync();
                        LiveParser.text(live, item.getJson());
                        return ConfigResult.live(item, live);
                    }
                    return ConfigResult.json(item, Json.parse(item.getJson()).getAsJsonObject(), false);
                } catch (Throwable ignored) {
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

    private String normalizeConfigUrl(String url) {
        return url == null ? "" : url.trim();
    }

    private void cacheConfig(Config item, JsonObject object) {
        cacheConfig(item, object.toString());
    }

    private void cacheConfig(Config item, String text) {
        if (item == null || TextUtils.isEmpty(text) || TextUtils.isEmpty(item.getUrl())) return;
        item.json(text).time(System.currentTimeMillis()).save();
    }

    private JsonObject loadObject(JsonObject object, int depth) throws Throwable {
        if (depth > 5) throw new IllegalStateException("Too many redirects.");
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
                return loadObject(Json.parse(Decoder.getJson(item.getUrl())).getAsJsonObject(), depth);
            } catch (Throwable e) {
                error = e;
                e.printStackTrace();
            }
        }
        if (error != null) throw error;
        throw new IllegalStateException(ResUtil.getString(R.string.error_config_parse));
    }

    private void mergeConfig(JsonObject target, JsonObject source) {
        String spider = Json.safeString(source, "spider");
        JsonArray lives = source.has("lives") ? source.getAsJsonArray("lives") : new JsonArray();
        if (!target.has("lives")) target.add("lives", new JsonArray());
        JsonArray targetLives = target.getAsJsonArray("lives");
        for (JsonElement element : lives) {
            JsonObject item = element.getAsJsonObject().deepCopy();
            String api = Json.safeString(item, "api");
            String jar = Json.safeString(item, "jar");
            if (TextUtils.isEmpty(jar) && api.startsWith("csp_")) item.addProperty("jar", spider);
            targetLives.add(item);
        }
        appendArray(target, source, "rules");
        appendArray(target, source, "headers");
        appendArray(target, source, "proxy");
        appendArray(target, source, "hosts");
        appendArray(target, source, "ads");
    }

    private void appendArray(JsonObject target, JsonObject source, String key) {
        if (!source.has(key)) return;
        if (!target.has(key)) target.add(key, new JsonArray());
        JsonArray targetArray = target.getAsJsonArray(key);
        for (JsonElement element : source.getAsJsonArray(key)) targetArray.add(element.deepCopy());
    }

    private void parseConfig(String text, Callback callback) {
        try {
            parseConfigOrThrow(text);
            if (callback != null) App.post(callback::success);
        } catch (Throwable e) {
            e.printStackTrace();
            if (callback != null) App.post(() -> callback.error(Notify.getError(R.string.error_config_parse, e)));
        }
    }

    private void parseText(String text, Callback callback) {
        try {
            parseTextOrThrow(text);
            if (callback != null) App.post(callback::success);
        } catch (Throwable e) {
            e.printStackTrace();
            if (callback != null) App.post(() -> callback.error(Notify.getError(R.string.error_config_parse, e)));
        }
    }

    private synchronized void parseText(String url, String text) {
        Live live = new Live(parseName(url), url).sync();
        LiveParser.text(live, text);
        if (liveMap.containsKey(live.getName())) return;
        lives.add(live);
        liveMap.put(live.getName(), live);
        if (home == null) setHome(live, true);
    }

    private String parseName(String url) {
        Uri uri = Uri.parse(url);
        if ("file".equals(uri.getScheme())) return new File(url).getName();
        if (uri.getLastPathSegment() != null) return uri.getLastPathSegment();
        if (uri.getQuery() != null) return uri.getQuery();
        if (uri.getHost() != null) return uri.getHost();
        return url;
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
        try {
            parseDepotOrThrow(object);
            if (callback != null) App.post(callback::success);
        } catch (Throwable e) {
            e.printStackTrace();
            if (callback != null) App.post(() -> callback.error(Notify.getError(R.string.error_config_get, e)));
        }
    }

    private void parseDepotOrThrow(JsonObject object) throws Throwable {
        List<Depot> items = Depot.arrayFrom(object.getAsJsonArray("urls").toString());
        if (items.isEmpty()) throw new IllegalStateException(ResUtil.getString(R.string.error_config_parse));
        Config.delete(config.getUrl());
        Throwable error = null;
        for (Depot item : items) {
            try {
                Config target = Config.find(item, 1);
                String text = loadDepotConfig(target);
                clear();
                config(target);
                parseConfigOrThrow(text);
                return;
            } catch (Throwable e) {
                error = e;
                e.printStackTrace();
            }
        }
        if (error != null) throw error;
        throw new IllegalStateException("No valid config");
    }

    private String loadDepotConfig(Config target) throws Throwable {
        if (!TextUtils.isEmpty(target.getJson())) {
            if (!target.isCache()) ThreadPools.config().execute(() -> refreshDepotCache(target));
            return target.getJson();
        }
        String text = Decoder.getJson(target.getUrl());
        if (Json.invalid(text)) return text;
        JsonObject loaded = loadObject(Json.parse(text).getAsJsonObject(), 0);
        cacheConfig(target, loaded);
        return loaded.toString();
    }

    private void refreshDepotCache(Config target) {
        try {
            String text = Decoder.getJson(target.getUrl());
            if (!Json.invalid(text)) cacheConfig(target, loadObject(Json.parse(text).getAsJsonObject(), 0));
        } catch (Throwable ignored) {
        }
    }

    private synchronized void parseConfig(JsonObject object, Callback callback) {
        try {
            parseConfigOrThrow(object);
            if (callback != null) App.post(callback::success);
        } catch (Throwable e) {
            e.printStackTrace();
            if (callback != null) App.post(() -> callback.error(Notify.getError(R.string.error_config_parse, e)));
        }
    }

    private void parseConfigOrThrow(String text) throws Throwable {
        if (Json.invalid(text)) parseTextOrThrow(text);
        else {
            JsonObject object = Json.parse(text).getAsJsonObject();
            if (object.has("msg")) throw new IllegalStateException(object.get("msg").getAsString());
            if (object.has("urls")) parseDepotOrThrow(object);
            else parseConfigOrThrow(object);
        }
    }

    private void parseTextOrThrow(String text) throws Throwable {
        Live live = new Live(parseName(config.getUrl()), config.getUrl()).sync();
        LiveParser.text(live, text);
        lives.add(live);
        liveMap.put(live.getName(), live);
        setHome(live, true);
    }

    private void parseConfigOrThrow(JsonObject object) throws Throwable {
        initLive(object);
        initOther(object);
        preloadJars(object);
        if (persistCache) config.json(object.toString()).update();
    }

    private void initLive(JsonObject object) {
        String spider = Json.safeString(object, "spider");
        Map<String, Live> cache = new HashMap<>();
        for (Live live : AppDatabase.get().getLiveDao().getAll()) cache.put(live.getName(), live);
        for (JsonElement element : Json.safeListElement(object, "lives")) {
            Live live = Live.objectFrom(element, spider);
            if (liveMap.containsKey(live.getName())) {
                Live old = liveMap.get(live.getName());
                if (old != null) {
                    if (!old.getUrl().equals(live.getUrl()) || !old.getApi().equals(live.getApi())) old.getGroups().clear();
                    old.setUrl(live.getUrl());
                    old.setApi(live.getApi());
                    old.setExt(live.getExt());
                    old.setJar(live.getJar());
                    old.setSpider(null);
                }
                continue;
            }
            live.setJar(parseJar(live, spider));
            lives.add(live.sync(cache));
            liveMap.put(live.getName(), live);
        }
        for (Live live : lives) {
            if (live.getName().equals(config.getHome())) {
                setHome(live, true);
            }
        }
    }

    private void preloadJars(JsonObject object) {
        Set<String> jars = new LinkedHashSet<>();
        jars.add(Json.safeString(object, "spider"));
        for (Live live : lives) jars.add(live.getJar());
        for (String jar : jars) BaseLoader.get().parseJar(jar);
    }

    private void initOther(JsonObject object) {
        if (home == null) setHome(lives.isEmpty() ? new Live() : lives.get(0), true);
        setHeaders(Header.arrayFrom(object.get("headers")));
        setProxy(Proxy.arrayFrom(object.get("proxy")));
        setHosts(Json.safeListString(object, "hosts"));
        setRules(Rule.arrayFrom(object.getAsJsonArray("rules")));
        VodConfig.refreshNetwork();
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
        parseConfig(object, null);
    }

    public void setKeep(Channel channel) {
        if (home == null || channel.getGroup().isHidden() || channel.getUrls().isEmpty()) return;
        Setting.putKeep(home.getName() + AppDatabase.SYMBOL + channel.getGroup().getName() + AppDatabase.SYMBOL + channel.getName() + AppDatabase.SYMBOL + channel.getCurrent());
    }

    public void setKeep(List<Group> items) {
        Set<String> keys = new HashSet<>();
        for (Keep keep : Keep.getLive()) keys.add(keep.getKey());
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
        return lives == null ? lives = new ArrayList<>() : lives;
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
        setHome(home, false);
    }

    private void setHome(Live home, boolean check) {
        this.home = home;
        this.home.setActivated(true);
        config.home(home.getName()).update();
        for (Live item : getLives()) item.setActivated(home);
        if (App.activity() != null && App.activity() instanceof LiveActivity) return;
        if (check) if (home.isBoot() || Setting.isBootLive()) App.post(this::bootLive);
    }

    private static class ConfigResult {

        private final Config config;
        private final JsonObject object;
        private final Live live;
        private final String text;
        private final Throwable error;
        private final boolean cacheable;

        private ConfigResult(Config config, JsonObject object, Live live, String text, Throwable error, boolean cacheable) {
            this.config = config;
            this.object = object;
            this.live = live;
            this.text = text;
            this.error = error;
            this.cacheable = cacheable;
        }

        private static ConfigResult json(Config config, JsonObject object, boolean cacheable) {
            return new ConfigResult(config, object, null, null, null, cacheable);
        }

        private static ConfigResult live(Config config, Live live) {
            return new ConfigResult(config, null, live, null, null, false);
        }

        private static ConfigResult text(Config config, String text) {
            return new ConfigResult(config, null, null, text, null, false);
        }

        private static ConfigResult error(Config config, Throwable error) {
            return new ConfigResult(config, null, null, null, error, false);
        }
    }
}
