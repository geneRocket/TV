package com.fongmi.android.tv.api.config;

import android.text.TextUtils;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.Setting;
import com.fongmi.android.tv.api.Decoder;
import com.fongmi.android.tv.api.loader.BaseLoader;
import com.fongmi.android.tv.bean.Config;
import com.fongmi.android.tv.bean.Depot;
import com.fongmi.android.tv.bean.Parse;
import com.fongmi.android.tv.bean.Rule;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.impl.Callback;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.UrlUtil;
import com.github.catvod.bean.Doh;
import com.github.catvod.bean.Header;
import com.github.catvod.bean.Proxy;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Json;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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
    private boolean loadLive;
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
        get().init().clear().config(config).load(callback);
    }

    public static void load(Config config, Callback callback, boolean loadLive) {
        get().init().clear().config(config).load(callback, loadLive);
    }

    public static void load(List<Config> configs, Callback callback) {
        if (configs == null || configs.isEmpty()) return;
        get().init().clear().config(configs.get(0)).loadMulti(configs, callback);
    }

    public static void load(List<Config> configs, Callback callback, boolean loadLive) {
        if (configs == null || configs.isEmpty()) return;
        get().init().clear().config(configs.get(0)).loadMulti(configs, callback, loadLive);
    }

    public VodConfig init() {
        this.wall = null;
        this.home = null;
        this.parse = null;
        this.config = Config.vod();
        this.ads = new ArrayList<>();
        this.doh = new ArrayList<>();
        this.hosts = new ArrayList<>();
        this.proxy = new ArrayList<>();
        this.rules = new ArrayList<>();
        this.headers = new ArrayList<>();
        this.ruleHosts = new ArrayList<>();
        this.loadUrls = new ArrayList<>();
        this.sites = new ArrayList<>();
        this.flags = new ArrayList<>();
        this.parses = new ArrayList<>();
        this.loadLive = false;
        return this;
    }

    public VodConfig config(Config config) {
        this.config = config;
        return this;
    }

    public VodConfig clear() {
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
        if (cache) App.execute(() -> loadConfigCache(callback));
        else App.execute(() -> loadConfig(callback));
    }

    public void loadMulti(List<Config> configs, Callback callback) {
        App.execute(() -> loadConfigs(configs, callback));
    }

    public void loadMulti(List<Config> configs, Callback callback, boolean loadLive) {
        this.loadLive = loadLive;
        App.execute(() -> loadConfigs(configs, callback));
    }

    private void loadConfig(Callback callback) {
        try {
            setLoadUrls(Collections.singletonList(config.getUrl()));
            checkJson(Json.parse(Decoder.getJson(config.getUrl())).getAsJsonObject(), callback);
        } catch (Throwable e) {
            if (TextUtils.isEmpty(config.getUrl())) App.post(() -> callback.error(""));
            else loadCache(callback, e);
            e.printStackTrace();
        }
    }

    private void loadConfigs(List<Config> configs, Callback callback) {
        List<String> urls = new ArrayList<>();
        JsonObject merged = new JsonObject();
        Throwable error = null;
        int success = 0;
        for (Config item : configs) {
            try {
                urls.add(item.getUrl());
                JsonObject object = loadObject(item.getUrl(), 0);
                mergeConfig(merged, object);
                success++;
            } catch (Throwable e) {
                error = e;
                e.printStackTrace();
            }
        }
        setLoadUrls(urls);
        if (success > 0) parseConfig(merged, callback);
        else if (!TextUtils.isEmpty(config.getJson())) checkJson(Json.parse(config.getJson()).getAsJsonObject(), callback);
        else {
            Throwable cause = error == null ? new Throwable("No valid config") : error;
            App.post(() -> callback.error(Notify.getError(R.string.error_config_get, cause)));
        }
    }

    private void setLoadUrls(List<String> urls) {
        this.loadUrls.clear();
        if (urls != null) this.loadUrls.addAll(urls);
    }

    private JsonObject loadObject(String url, int depth) throws Throwable {
        if (depth > 5) throw new IllegalStateException("Too many redirects.");
        JsonObject object = Json.parse(Decoder.getJson(url)).getAsJsonObject();
        if (object.has("msg")) throw new IllegalStateException(object.get("msg").getAsString());
        if (!object.has("urls")) return object;
        List<Depot> items = Depot.arrayFrom(object.getAsJsonArray("urls").toString());
        if (items.isEmpty()) throw new IllegalStateException(ResUtil.getString(R.string.error_config_parse));
        return loadObject(items.get(0).getUrl(), depth + 1);
    }

    private void mergeConfig(JsonObject target, JsonObject source) {
        JsonObject root = source.has("video") ? source.getAsJsonObject("video") : source;
        mergeSites(target, root);
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

    private void mergeSites(JsonObject target, JsonObject source) {
        String spider = Json.safeString(source, "spider");
        JsonArray sites = source.has("sites") ? source.getAsJsonArray("sites") : new JsonArray();
        if (!target.has("sites")) target.add("sites", new JsonArray());
        JsonArray targetSites = target.getAsJsonArray("sites");
        for (JsonElement element : sites) {
            JsonObject item = element.getAsJsonObject().deepCopy();
            String api = Json.safeString(item, "api");
            String jar = Json.safeString(item, "jar");
            if (TextUtils.isEmpty(jar) && api.startsWith("csp_")) item.addProperty("jar", spider);
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
        if (!TextUtils.isEmpty(config.getJson()) && config.isCache()) checkJson(Json.parse(config.getJson()).getAsJsonObject(), callback);
        else loadConfig(callback);
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
        for (Depot item : items) configs.add(Config.find(item, 0));
        Config.delete(config.getUrl());
        config = configs.get(0);
        loadConfig(callback);
    }

    private void parseConfig(JsonObject object, Callback callback) {
        try {
            initSite(object);
            initParse(object);
            initOther(object);
            BaseLoader.get().parseJar(Json.safeString(object, "spider"));
            if (loadLive && object.has("lives")) initLive(object);
            String notice = Json.safeString(object, "notice");
            config.logo(Json.safeString(object, "logo"));
            App.post(() -> callback.success(notice));
            config.json(object.toString()).update();
            App.post(callback::success);
        } catch (Throwable e) {
            e.printStackTrace();
            App.post(() -> callback.error(Notify.getError(R.string.error_config_parse, e)));
        }
    }

    private void initSite(JsonObject object) {
        if (object.has("video")) {
            initSite(object.getAsJsonObject("video"));
            return;
        }
        String spider = Json.safeString(object, "spider");
        for (JsonElement element : Json.safeListElement(object, "sites")) {
            Site site = Site.objectFrom(element, spider);
            if (sites.contains(site)) continue;
            site.setJar(parseJar(site, spider));
            sites.add(site.trans().sync());
        }
        for (Site site : sites) {
            if (site.getKey().equals(config.getHome())) {
                setHome(site);
            }
        }
    }

    private void initLive(JsonObject object) {
        Config temp = Config.find(config, 1).save();
        boolean sync = false;
        for (String url : loadUrls) {
            if (LiveConfig.get().needSync(url)) {
                sync = true;
                break;
            }
        }
        if (loadUrls.isEmpty()) sync = LiveConfig.get().needSync(config.getUrl());
        if (sync) {
            LiveConfig.get().clear().config(temp).parse(object);
            putLiveSetting(loadUrls.isEmpty() ? Collections.singletonList(temp.getUrl()) : loadUrls);
        }
    }

    private void putLiveSetting(List<String> urls) {
        List<String> values = new ArrayList<>();
        List<String> names = new ArrayList<>();
        for (String url : urls) {
            if (TextUtils.isEmpty(url) || values.contains(url)) continue;
            values.add(url);
            names.add(Config.find(url, 1).getDesc());
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

    private void initParse(JsonObject object) {
        for (JsonElement element : Json.safeListElement(object, "parses")) {
            Parse parse = Parse.objectFrom(element);
            if (parse.getName().equals(config.getParse()) && parse.getType() > 1) setParse(parse);
            if (!parses.contains(parse)) parses.add(parse);
        }
    }

    private void initOther(JsonObject object) {
        if (parses.size() > 0) parses.add(0, Parse.god());
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
        int index = getParses().indexOf(Parse.get(name));
        return index == -1 ? null : getParses().get(index);
    }

    public Site getSite(String key) {
        int index = getSites().indexOf(Site.get(key));
        return index == -1 ? new Site() : getSites().get(index);
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
        config.home(home.getKey()).save();
        for (Site item : getSites()) item.setActivated(home);
    }

    private void setWall(String wall) {
        this.wall = wall;
        boolean load = !TextUtils.isEmpty(wall) && WallConfig.get().needSync(wall);
        if (load) WallConfig.get().config(Config.find(wall, config.getName(), 2).update());
    }
}
