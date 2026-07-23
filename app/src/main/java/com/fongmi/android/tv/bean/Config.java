package com.fongmi.android.tv.bean;

import com.fongmi.android.tv.repository.HistoryRepository;
import com.fongmi.android.tv.repository.KeepRepository;

import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.Setting;
import com.fongmi.android.tv.db.AppDatabase;
import com.fongmi.android.tv.utils.FileUtil;
import com.github.catvod.utils.Path;
import com.github.catvod.utils.Prefers;
import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Entity(indices = @Index(value = {"url", "type"}, unique = true))
public class Config {

    private static final Map<Integer, Config> ID_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, Config> URL_CACHE = new ConcurrentHashMap<>();
    private static final Map<Integer, List<Config>> TYPE_CACHE = new ConcurrentHashMap<>();

    public static void clearCache() {
        ID_CACHE.clear();
        URL_CACHE.clear();
        TYPE_CACHE.clear();
    }

    public static class UrlItem {

        private int id;
        private int type;
        private long time;
        private String url;
        private String name;

        public int getId() {
            return id;
        }

        public void setId(int id) {
            this.id = id;
        }

        public int getType() {
            return type;
        }

        public void setType(int type) {
            this.type = type;
        }

        public long getTime() {
            return time;
        }

        public void setTime(long time) {
            this.time = time;
        }

        public String getUrl() {
            return url == null ? "" : url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public String getName() {
            return name == null ? "" : name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }

    @PrimaryKey(autoGenerate = true)
    @SerializedName("id")
    private int id;
    @SerializedName("type")
    private int type;
    @SerializedName("time")
    private long time;
    @SerializedName("url")
    private String url;
    @SerializedName("json")
    private String json;
    @SerializedName("name")
    private String name;
    @SerializedName("logo")
    private String logo;
    @SerializedName("home")
    private String home;
    @SerializedName("parse")
    private String parse;

    public static List<Config> arrayFrom(String str) {
        Type listType = new TypeToken<List<Config>>() {}.getType();
        List<Config> items = App.gson().fromJson(str, listType);
        return items == null ? Collections.emptyList() : items;
    }

    public static Config objectFrom(String str) {
        return App.gson().fromJson(str, Config.class);
    }

    public static Config create(int type) {
        return new Config().type(type);
    }

    public static Config create(int type, String url) {
        return new Config().type(type).url(url).insert();
    }

    public static Config create(int type, String url, String name) {
        return new Config().type(type).url(url).name(name).insert();
    }

    private static String cacheKey(String url, int type) {
        return type + "@" + (url == null ? "" : url);
    }

    private static Config cache(Config item) {
        if (item == null || item.isEmpty()) return item;
        Config old = ID_CACHE.get(item.getId());
        if (old != null) {
            String oldKey = cacheKey(old.getUrl(), old.getType());
            String newKey = cacheKey(item.getUrl(), item.getType());
            if (!oldKey.equals(newKey)) URL_CACHE.remove(oldKey);
            if (old.getType() != item.getType()) TYPE_CACHE.remove(old.getType());
        }
        ID_CACHE.put(item.getId(), item);
        URL_CACHE.put(cacheKey(item.getUrl(), item.getType()), item);
        TYPE_CACHE.remove(item.getType());
        return item;
    }

    private static List<Config> cache(int type, List<Config> items) {
        List<Config> cached = new ArrayList<>(items);
        for (Config item : cached) cache(item);
        TYPE_CACHE.put(type, cached);
        return cached;
    }

    private static Config copy(Config item) {
        if (item == null) return null;
        return new Config()
                .type(item.getType())
                .url(item.getUrl())
                .json(item.getJson())
                .name(item.getName())
                .logo(item.getLogo())
                .home(item.getHome())
                .parse(item.getParse())
                .time(item.getTime())
                .id(item.getId());
    }

    private static List<Config> copy(List<Config> items) {
        List<Config> copied = new ArrayList<>(items.size());
        for (Config item : items) copied.add(copy(item));
        return copied;
    }

    private static List<Config> fromUrls(List<UrlItem> items) {
        List<Config> configs = new ArrayList<>(items.size());
        for (UrlItem item : items) {
            configs.add(new Config()
                    .id(item.getId())
                    .type(item.getType())
                    .time(item.getTime())
                    .url(item.getUrl())
                    .name(item.getName()));
        }
        return configs;
    }

    private static void removeCache(String url, int type) {
        Config item = URL_CACHE.remove(cacheKey(url, type));
        if (item != null) ID_CACHE.remove(item.getId());
        TYPE_CACHE.remove(type);
    }

    private static void removeCache(String url) {
        for (Config item : new ArrayList<>(URL_CACHE.values())) {
            if (item != null && item.getUrl().equals(url)) removeCache(item.getUrl(), item.getType());
        }
    }

    private static void removeTypeCache(int type) {
        for (Config item : new ArrayList<>(URL_CACHE.values())) {
            if (item != null && item.getType() == type) {
                URL_CACHE.remove(cacheKey(item.getUrl(), type));
                ID_CACHE.remove(item.getId());
            }
        }
        TYPE_CACHE.remove(type);
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getType() {
        return type;
    }

    public void setType(int type) {
        this.type = type;
    }

    public String getUrl() {
        return url == null ? "" : url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getJson() {
        return json == null ? "" : json;
    }

    public void setJson(String json) {
        this.json = json;
    }

    public String getName() {
        return name == null ? "" : name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getLogo() {
        return logo == null ? "" : logo;
    }

    public void setLogo(String logo) {
        this.logo = logo;
    }

    public String getHome() {
        return home == null ? "" : home;
    }

    public void setHome(String home) {
        this.home = home;
    }

    public String getParse() {
        return parse == null ? "" : parse;
    }

    public void setParse(String parse) {
        this.parse = parse;
    }

    public long getTime() {
        return time;
    }

    public void setTime(long time) {
        this.time = time;
    }

    public boolean isCache() {
        return getTime() + (long)(3600*1000*12 * Setting.getConfigCache()) > System.currentTimeMillis();
    }

    public Config type(int type) {
        setType(type);
        return this;
    }

    public Config url(String url) {
        setUrl(url);
        return this;
    }

    public Config json(String json) {
        setJson(json);
        return this;
    }

    public Config name(String name) {
        setName(name);
        return this;
    }

    public Config logo(String logo) {
        setLogo(logo);
        return this;
    }

    public Config home(String home) {
        setHome(home);
        return this;
    }

    public Config parse(String parse) {
        setParse(parse);
        return this;
    }

    public Config time(long time) {
        setTime(time);
        return this;
    }

    public Config id(int id) {
        setId(id);
        return this;
    }

    public boolean isEmpty() {
        return TextUtils.isEmpty(getUrl());
    }

    public String getDesc() {
        if (!TextUtils.isEmpty(getName())) return getName();
        if (!TextUtils.isEmpty(getUrl())) return getUrl();
        return "";
    }

    // DB Read operations - Should be called from background thread
    public static List<Config> getAll(int type) {
        List<Config> items = TYPE_CACHE.get(type);
        if (items != null) return copy(items);
        return copy(cache(type, AppDatabase.get().getConfigDao().findByType(type)));
    }

    public static List<Config> findUrls() {
        return fromUrls(AppDatabase.get().getConfigDao().findUrlByType(0));
    }

    public static void delete(String url) {
        App.execute(() -> {
            AppDatabase.get().getConfigDao().delete(url);
            removeCache(url);
        });
    }

    public static void delete(String url, int type) {
        App.execute(() -> {
            if (type == 2) Path.clear(FileUtil.getWall(0));
            if (type == 2) AppDatabase.get().getConfigDao().delete(type);
            else AppDatabase.get().getConfigDao().delete(url, type);
            if (type == 2) removeTypeCache(type);
            else removeCache(url, type);
        });
    }

    public static Config vod() {
        Config item = TYPE_CACHE.containsKey(0) && !TYPE_CACHE.get(0).isEmpty() ? TYPE_CACHE.get(0).get(0) : AppDatabase.get().getConfigDao().findOne(0);
        return item == null ? copy(create(0)) : copy(cache(item));
    }

    public static Config live() {
        Config item = TYPE_CACHE.containsKey(1) && !TYPE_CACHE.get(1).isEmpty() ? TYPE_CACHE.get(1).get(0) : AppDatabase.get().getConfigDao().findOne(1);
        return item == null ? copy(create(1)) : copy(cache(item));
    }

    public static Config wall() {
        Config item = TYPE_CACHE.containsKey(2) && !TYPE_CACHE.get(2).isEmpty() ? TYPE_CACHE.get(2).get(0) : AppDatabase.get().getConfigDao().findOne(2);
        return item == null ? copy(create(2)) : copy(cache(item));
    }

    public static Config find(int id) {
        Config item = ID_CACHE.get(id);
        return item != null ? copy(item) : copy(cache(AppDatabase.get().getConfigDao().findById(id)));
    }

    public static Config find(String url, int type) {
        Config item = URL_CACHE.get(cacheKey(url, type));
        if (item == null) item = cache(AppDatabase.get().getConfigDao().find(url, type));
        return item == null ? copy(create(type, url)) : copy(item).type(type);
    }

    public static Config find(String url, String name, int type) {
        Config item = URL_CACHE.get(cacheKey(url, type));
        if (item == null) item = cache(AppDatabase.get().getConfigDao().find(url, type));
        if (item == null) return copy(create(type, url, name));
        Config config = copy(item).type(type).name(name);
        if (!TextUtils.isEmpty(name) && !name.equals(item.getName())) config.save();
        return config;
    }

    public static Config find(Config config) {
        return find(config, config.getType());
    }

    public static Config find(Config config, int type) {
        Config item = URL_CACHE.get(cacheKey(config.getUrl(), type));
        if (item == null) item = cache(AppDatabase.get().getConfigDao().find(config.getUrl(), type));
        if (item == null) return copy(create(type, config.getUrl(), config.getName()));
        Config result = copy(item).type(type).name(config.getName());
        if (!TextUtils.isEmpty(config.getName()) && !config.getName().equals(item.getName())) result.save();
        return result;
    }

    public static Config find(Depot depot, int type) {
        Config item = URL_CACHE.get(cacheKey(depot.getUrl(), type));
        if (item == null) item = cache(AppDatabase.get().getConfigDao().find(depot.getUrl(), type));
        if (item == null) return copy(create(type, depot.getUrl(), depot.getName()));
        Config config = copy(item).type(type).name(depot.getName());
        if (!TextUtils.isEmpty(depot.getName()) && !depot.getName().equals(item.getName())) config.save();
        return config;
    }

    // Synchronous write methods (safe if caller is in App.execute())
    public Config insert() {
        if (isEmpty()) return this;
        setId(Math.toIntExact(AppDatabase.get().getConfigDao().insert(this)));
        return cache(this);
    }

    public Config save() {
        if (isEmpty()) return this;
        AppDatabase.get().getConfigDao().update(this);
        return cache(this);
    }

    public Config update() {
        if (isEmpty()) return this;
        setTime(System.currentTimeMillis());
        Prefers.put("config_" + getType(), getUrl());
        return save();
    }

    public void delete() {
        App.execute(() -> {
            AppDatabase.get().getConfigDao().delete(getUrl(), getType());
            removeCache(getUrl(), getType());
            // Assuming History and Keep also have their own DB accessors
            HistoryRepository.get().delete(getId());
            KeepRepository.get().delete(getId());
        });
    }

    @NonNull
    @Override
    public String toString() {
        return App.gson().toJson(this);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof Config)) return false;
        Config it = (Config) obj;
        return getId() == it.getId();
    }
}
