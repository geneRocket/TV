package com.fongmi.android.tv.api.config;

import com.fongmi.android.tv.utils.TextValues;

import com.fongmi.android.tv.api.Decoder;
import com.fongmi.android.tv.bean.Config;
import com.fongmi.android.tv.bean.Depot;
import com.fongmi.android.tv.repository.ConfigRepository;
import com.github.catvod.utils.Json;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/** Downloads and validates documents; has no access to the active runtime configuration. */
final class VodConfigLoader {
    interface Reader { String read(String url) throws Throwable; }
    private final Reader reader;
    private final Consumer<Depot> register;
    VodConfigLoader() { this(Decoder::getJson, item -> ConfigRepository.get().find(item, 0)); }
    VodConfigLoader(Reader reader) { this(reader, item -> { }); }
    VodConfigLoader(Reader reader, Consumer<Depot> register) {
        this.reader = reader;
        this.register = register;
    }
    Result readCachedConfig(Config item) {
        ConfigLoadTask.check();
        if (TextValues.isEmpty(item.getJson())) return null;
        try {
            JsonObject object = Json.parse(item.getJson()).getAsJsonObject();
            ConfigPayloadValidator.vod(object);
            return Result.success(item, object, false);
        } catch (Throwable error) {
            ConfigLoadTask.check();
            return null;
        }
    }

    Result loadResult(Config item) {
        try {
            return Result.success(item, loadObject(item.getUrl(), 0), true);
        } catch (Throwable e) {
            ConfigLoadTask.check();
            Result cached = readCachedConfig(item);
            if (cached != null) return cached;
            return Result.error(item, e);
        }
    }

    List<Config> getUniqueConfigs(List<Config> configs) {
        List<Config> items = new ArrayList<>();
        Set<String> loaded = new LinkedHashSet<>();
        for (Config item : configs) {
            String url = normalizeConfigUrl(item.getUrl());
            if (TextValues.isEmpty(url) || !loaded.add(url)) continue;
            if (!url.equals(item.getUrl())) item.url(url);
            items.add(item);
        }
        return items;
    }

    List<String> getConfigUrls(List<Config> configs) {
        List<String> urls = new ArrayList<>();
        Set<String> loaded = new LinkedHashSet<>();
        for (Config item : configs) {
            String url = normalizeConfigUrl(item.getUrl());
            if (TextValues.isEmpty(url) || !loaded.add(url)) continue;
            urls.add(url);
        }
        return urls;
    }

    void cacheConfig(Config item, JsonObject object) {
        if (item == null || object == null || TextValues.isEmpty(item.getUrl())) return;
        ConfigLoadTask.check();
        ConfigPayloadValidator.vod(object);
        Config.cacheContent(item, object.toString(), ConfigLoadTask.startedAt());
    }

    String normalizeConfigUrl(String url) {
        return url == null ? "" : url.trim();
    }

    JsonObject loadObject(String url, int depth) throws Throwable {
        return new ConfigJsonLoader(reader::read, ConfigPayloadValidator::vod, register).load(url, depth);
    }

    void refreshDepotCache(Config target) {
        try {
            cacheConfig(target, loadObject(target.getUrl(), 0));
        } catch (Throwable ignored) {
        }
    }

    static class Result {

        final Config config;
        final JsonObject object;
        final Throwable error;
        final boolean cacheable;

        private Result(Config config, JsonObject object, Throwable error, boolean cacheable) {
            this.config = config;
            this.object = object;
            this.error = error;
            this.cacheable = cacheable;
        }

        private static Result success(Config config, JsonObject object, boolean cacheable) {
            return new Result(config, object, null, cacheable);
        }

        private static Result error(Config config, Throwable error) {
            return new Result(config, null, error, false);
        }
    }

}
