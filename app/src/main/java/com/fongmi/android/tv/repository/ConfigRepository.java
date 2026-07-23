package com.fongmi.android.tv.repository;

import com.fongmi.android.tv.bean.Config;
import com.fongmi.android.tv.bean.Depot;

import java.util.List;

/** Data-access boundary for persisted VOD, live and wallpaper configurations. */
public final class ConfigRepository {

    private static final ConfigRepository INSTANCE = new ConfigRepository();

    public static ConfigRepository get() {
        return INSTANCE;
    }

    private ConfigRepository() {
    }

    public List<Config> all(int type) {
        return Config.getAll(type);
    }

    public List<Config> urls() {
        return Config.findUrls();
    }

    public Config find(int id) {
        return Config.find(id);
    }

    public Config find(String url, int type) {
        return Config.find(url, type);
    }

    public Config find(String url, String name, int type) {
        return Config.find(url, name, type);
    }

    public Config find(Config config) {
        return Config.find(config);
    }

    public Config find(Config config, int type) {
        return Config.find(config, type);
    }

    public Config find(Depot depot, int type) {
        return Config.find(depot, type);
    }

    public Config save(Config config) {
        return config.save();
    }

    public void delete(Config config) {
        config.delete();
    }

    public void delete(String url, int type) {
        Config.delete(url, type);
    }

    public void delete(String url) {
        Config.delete(url);
    }

    public void clearMemoryCache() {
        Config.clearCache();
    }
}
