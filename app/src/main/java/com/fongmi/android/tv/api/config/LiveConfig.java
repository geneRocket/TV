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
import java.util.List;

public class LiveConfig {

    private List<Live> lives;
    private List<Rule> rules;
    private List<String> ads;
    private List<Header> headers;
    private List<Proxy> proxy;
    private List<String> hosts;
    private List<String> ruleHosts;
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

    public static void load(List<Config> configs, Callback callback) {
        if (configs == null || configs.isEmpty()) return;
        get().init().clear().config(configs.get(0)).loadMulti(configs, callback);
    }

    public LiveConfig init() {
        this.home = null;
        this.ads = new ArrayList<>();
        this.hosts = new ArrayList<>();
        this.proxy = new ArrayList<>();
        this.rules = new ArrayList<>();
        this.headers = new ArrayList<>();
        this.ruleHosts = new ArrayList<>();
        this.lives = new ArrayList<>();
        return config(Config.live());
    }

    public LiveConfig config(Config config) {
        this.config = config;
        if (config.getUrl() == null) return this;
        this.sync = config.getUrl().equals(VodConfig.getUrl());
        return this;
    }

    public LiveConfig clear() {
        for (Live live : lives) BaseLoader.get().clearLive(live.getName(), live.getApi(), live.getExt(), live.getJar());
        this.home = null;
        this.ads.clear();
        this.hosts.clear();
        this.proxy.clear();
        this.rules.clear();
        this.headers.clear();
        this.ruleHosts.clear();
        this.lives.clear();
        return this;
    }

    public void load() {
        if (isEmpty()) load(new Callback());
    }

    public void load(Callback callback) {
        App.execute(() -> loadConfig(callback));
    }

    public void loadMulti(List<Config> configs, Callback callback) {
        App.execute(() -> loadConfigs(configs, callback));
    }

    private void loadConfig(Callback callback) {
        try {
            parseConfig(Decoder.getJson(config.getUrl()), callback);
        } catch (Throwable e) {
            if (TextUtils.isEmpty(config.getUrl())) App.post(() -> callback.error(""));
            else App.post(() -> callback.error(Notify.getError(R.string.error_config_get, e)));
            e.printStackTrace();
        }
    }

    private void loadConfigs(List<Config> configs, Callback callback) {
        JsonObject merged = new JsonObject();
        Throwable error = null;
        int success = 0;
        for (Config item : configs) {
            try {
                String text = Decoder.getJson(item.getUrl());
                if (Json.invalid(text)) {
                    parseText(item.getUrl(), text);
                    success++;
                    continue;
                }
                JsonObject object = loadObject(Json.parse(text).getAsJsonObject(), 0);
                mergeConfig(merged, object);
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

    private JsonObject loadObject(JsonObject object, int depth) throws Throwable {
        if (depth > 5) throw new IllegalStateException("Too many redirects.");
        if (object.has("msg")) throw new IllegalStateException(object.get("msg").getAsString());
        if (!object.has("urls")) return object;
        List<Depot> items = Depot.arrayFrom(object.getAsJsonArray("urls").toString());
        if (items.isEmpty()) throw new IllegalStateException(ResUtil.getString(R.string.error_config_parse));
        return loadObject(Json.parse(Decoder.getJson(items.get(0).getUrl())).getAsJsonObject(), depth + 1);
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
        if (Json.invalid(text)) {
            parseText(text, callback);
        } else {
            checkJson(Json.parse(text).getAsJsonObject(), callback);
        }
    }

    private void parseText(String text, Callback callback) {
        Live live = new Live(parseName(config.getUrl()), config.getUrl()).sync();
        LiveParser.text(live, text);
        lives.add(live);
        setHome(live, true);
        App.post(callback::success);
    }

    private void parseText(String url, String text) {
        Live live = new Live(parseName(url), url).sync();
        LiveParser.text(live, text);
        if (lives.contains(live)) return;
        lives.add(live);
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
        List<Depot> items = Depot.arrayFrom(object.getAsJsonArray("urls").toString());
        if (items.isEmpty()) {
            if (callback != null) App.post(() -> callback.error(ResUtil.getString(R.string.error_config_parse)));
            return;
        }
        List<Config> configs = new ArrayList<>();
        for (Depot item : items) configs.add(Config.find(item, 1));
        Config.delete(config.getUrl());
        config = configs.get(0);
        loadConfig(callback);
    }

    private void parseConfig(JsonObject object, Callback callback) {
        try {
            initLive(object);
            initOther(object);
            BaseLoader.get().parseJar(Json.safeString(object, "spider"));
            if (callback != null) App.post(callback::success);
        } catch (Throwable e) {
            e.printStackTrace();
            if (callback != null) App.post(() -> callback.error(Notify.getError(R.string.error_config_parse, e)));
        }
    }

    private void initLive(JsonObject object) {
        String spider = Json.safeString(object, "spider");
        for (JsonElement element : Json.safeListElement(object, "lives")) {
            Live live = Live.objectFrom(element, spider);
            if (lives.contains(live)) continue;
            live.setJar(parseJar(live, spider));
            lives.add(live.sync());
        }
        for (Live live : lives) {
            if (live.getName().equals(config.getHome())) {
                setHome(live, true);
            }
        }
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
        List<String> key = new ArrayList<>();
        for (Keep keep : Keep.getLive()) key.add(keep.getKey());
        for (Group group : items) {
            if (group.isKeep()) continue;
            for (Channel channel : group.getChannel()) {
                if (key.contains(channel.getName())) {
                    items.get(0).add(channel);
                }
            }
        }
    }

    public int[] find(List<Group> items) {
        String[] splits = Setting.getKeep().split(AppDatabase.SYMBOL);
        if (splits.length < 4 || !getHome().getName().equals(splits[0])) return new int[]{1, 0};
        for (int i = 0; i < items.size(); i++) {
            Group group = items.get(i);
            if (group.getName().equals(splits[1])) {
                int j = group.find(splits[2]);
                if (j != -1 && splits.length == 4) group.getChannel().get(j).setLine(splits[3]);
                if (j != -1) return new int[]{i, j};
            }
        }
        return new int[]{1, 0};
    }

    public int[] find(String number, List<Group> items) {
        for (int i = 0; i < items.size(); i++) {
            int j = items.get(i).find(Integer.parseInt(number));
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
        int index = getLives().indexOf(Live.get(key));
        return index == -1 ? new Live() : getLives().get(index);
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
}
