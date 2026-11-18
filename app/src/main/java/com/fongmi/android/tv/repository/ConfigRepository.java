package com.fongmi.android.tv.repository;

import com.fongmi.android.tv.bean.Config;
import com.fongmi.android.tv.Setting;
import com.fongmi.android.tv.utils.ConfigUrlParser;
import com.fongmi.android.tv.bean.Depot;

import java.util.ArrayList;
import java.util.List;

/** Data-access boundary for persisted VOD, live and wallpaper configurations. */
public final class ConfigRepository {

    private static final ConfigRepository INSTANCE = new ConfigRepository();

    public static ConfigRepository get() {
        return INSTANCE;
    }

    private ConfigRepository() {
    }

    public List<Config> selected(int type) {
        String urls = type == 0 ? Setting.getVodConfigUrls() : Setting.getLiveConfigUrls();
        List<Config> configs = new ArrayList<>();
        for (String url : ConfigUrlParser.parse(urls)) configs.add(find(url, type));
        if (configs.isEmpty()) configs.add(type == 0 ? Config.vod() : Config.live());
        return configs;
    }

    public void clearSelectedCache(int type) {
        for (Config config : selected(type)) config.json("").time(System.currentTimeMillis()).save();
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

    public Config prepareEdit(String previousUrl, String url, String name, int type) {
        return Config.prepareEdit(previousUrl, url, name, type);
    }

    public boolean rename(String url, String name, int type) {
        return Config.rename(url, name, type);
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
