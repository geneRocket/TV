package com.fongmi.android.tv.api.config;

import android.net.Uri;
import com.fongmi.android.tv.utils.TextValues;

import com.fongmi.android.tv.api.Decoder;
import com.fongmi.android.tv.api.LiveParser;
import com.fongmi.android.tv.bean.Channel;
import com.fongmi.android.tv.bean.Config;
import com.fongmi.android.tv.bean.Group;
import com.fongmi.android.tv.bean.Live;
import com.github.catvod.utils.Json;
import com.google.gson.JsonObject;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Document loading/validation with no dependency on the active live or VOD configuration. */
final class LiveConfigLoader {
    interface Reader { String read(String url) throws Throwable; }
    private final Reader reader;
    LiveConfigLoader() { this(Decoder::getJson); }
    LiveConfigLoader(Reader reader) { this.reader = reader; }
    Result loadResult(Config item) {
        try {
            String text = reader.read(item.getUrl());
            if (Json.invalid(text)) {
                Live live = new Live(parseName(item.getUrl()), item.getUrl()).sync();
                LiveParser.text(live, text);
                cacheConfig(item, text);
                return Result.live(item, live);
            }
            return Result.json(item, loadObject(Json.parse(text).getAsJsonObject(), 0), true);
        } catch (Throwable e) {
            ConfigLoadTask.check();
            Result cached = readCachedConfig(item);
            if (cached != null) return cached;
            return Result.error(item, e);
        }
    }

    Result readCachedConfig(Config item) {
        ConfigLoadTask.check();
        if (TextValues.isEmpty(item.getJson())) return null;
        try {
            if (Json.invalid(item.getJson())) {
                Live live = new Live(parseName(item.getUrl()), item.getUrl()).sync();
                LiveParser.text(live, item.getJson());
                requireChannels(live);
                return Result.live(item, live);
            }
            JsonObject object = Json.parse(item.getJson()).getAsJsonObject();
            ConfigPayloadValidator.live(object);
            return Result.json(item, object, false);
        } catch (Throwable error) {
            ConfigLoadTask.check();
            return null;
        }
    }

    void cacheConfig(Config item, JsonObject object) {
        cacheConfig(item, object.toString());
    }

    void cacheConfig(Config item, String text) {
        if (item == null || TextValues.isEmpty(text) || TextValues.isEmpty(item.getUrl())) return;
        ConfigLoadTask.check();
        if (Json.invalid(text)) validateText(item.getUrl(), text);
        else ConfigPayloadValidator.live(Json.parse(text).getAsJsonObject());
        Config.cacheContent(item, text, ConfigLoadTask.startedAt());
    }

    JsonObject loadObject(JsonObject object, int depth) throws Throwable { return new ConfigJsonLoader(reader::read, ConfigPayloadValidator::live).resolve(object, depth); }

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

    String normalizeConfigUrl(String url) {
        return url == null ? "" : url.trim();
    }

    String parseName(String url) {
        Uri uri = Uri.parse(url);
        if ("file".equals(uri.getScheme())) return new File(url).getName();
        if (uri.getLastPathSegment() != null) return uri.getLastPathSegment();
        if (uri.getQuery() != null) return uri.getQuery();
        if (uri.getHost() != null) return uri.getHost();
        return url;
    }

    void validateText(String url, String text) {
        Live candidate = new Live(parseName(url), url).sync();
        LiveParser.text(candidate, text);
        requireChannels(candidate);
    }

    void requireChannels(Live live) {
        for (Group group : live.getGroups()) {
            for (Channel channel : group.getChannel()) if (!channel.getUrls().isEmpty()) return;
        }
        throw new IllegalStateException("Invalid live playlist; keeping cached configuration");
    }

    void refreshDepotCache(Config target) {
        try {
            String text = reader.read(target.getUrl());
            if (Json.invalid(text)) cacheConfig(target, text);
            else cacheConfig(target, loadObject(Json.parse(text).getAsJsonObject(), 0));
        } catch (Throwable ignored) {
        }
    }
    static class Result {

        final Config config;
        final JsonObject object;
        final Live live;
        final Throwable error;
        final boolean cacheable;

        private Result(Config config, JsonObject object, Live live, Throwable error, boolean cacheable) {
            this.config = config;
            this.object = object;
            this.live = live;
            this.error = error;
            this.cacheable = cacheable;
        }

        private static Result json(Config config, JsonObject object, boolean cacheable) {
            return new Result(config, object, null, null, cacheable);
        }

        private static Result live(Config config, Live live) {
            return new Result(config, null, live, null, false);
        }

        private static Result error(Config config, Throwable error) {
            return new Result(config, null, null, error, false);
        }
    }
}
