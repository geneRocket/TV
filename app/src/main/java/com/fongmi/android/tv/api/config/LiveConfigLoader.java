package com.fongmi.android.tv.api.config;

import android.net.Uri;
import com.fongmi.android.tv.utils.TextValues;

import com.fongmi.android.tv.api.Decoder;
import com.fongmi.android.tv.api.LiveParser;
import com.fongmi.android.tv.bean.Channel;
import com.fongmi.android.tv.bean.Config;
import com.fongmi.android.tv.bean.Depot;
import com.fongmi.android.tv.bean.Group;
import com.fongmi.android.tv.bean.Live;
import com.fongmi.android.tv.repository.ConfigRepository;
import com.github.catvod.utils.Json;
import com.google.gson.JsonObject;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/** Document loading/validation with no dependency on the active live or VOD configuration. */
final class LiveConfigLoader {
    interface Reader { String read(String url) throws Throwable; }
    private final Reader reader;
    private final Consumer<Depot> register;
    private final BiConsumer<String, String> validatePlaylist;
    LiveConfigLoader() {
        this(Decoder::getJson, item -> ConfigRepository.get().find(item, 1), null);
    }
    LiveConfigLoader(Reader reader, Consumer<Depot> register, BiConsumer<String, String> validatePlaylist) {
        this.reader = reader;
        this.register = register;
        this.validatePlaylist = validatePlaylist == null ? this::validateText : validatePlaylist;
    }
    Result loadResult(Config item) {
        try {
            Payload payload = resolvePayload(item.getUrl(), reader.read(item.getUrl()), 0);
            if (payload.object == null) {
                Live live = new Live(parseName(item.getUrl()), payload.url).sync();
                LiveParser.text(live, payload.text);
                requireChannels(live);
                Config.cacheContent(item, payload.text, ConfigLoadTask.startedAt());
                return Result.live(item, live);
            }
            return Result.json(item, payload.object, true);
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

    Payload resolvePayload(String url, String text, int depth) throws Throwable {
        ConfigLoadTask.check();
        ConfigJsonLoader.requireDepth(depth);
        JsonObject object = parseObject(text);
        if (object == null) {
            if (depth > 0) validatePlaylist.accept(url, text);
            return new Payload(url, text, null);
        }
        if (object.has("msg")) throw new IllegalStateException(object.get("msg").getAsString());
        if (object.has("urls")) {
            return ConfigJsonLoader.resolveDepot(object, register,
                    source -> resolvePayload(source, reader.read(source), depth + 1));
        }
        ConfigPayloadValidator.live(object);
        return new Payload(url, text, object);
    }

    private static JsonObject parseObject(String text) {
        try {
            JsonElement value = JsonParser.parseString(text);
            return value.isJsonObject() ? value.getAsJsonObject() : null;
        } catch (JsonParseException error) {
            return null;
        }
    }

    static final class Payload {
        final String url, text;
        final JsonObject object;

        Payload(String url, String text, JsonObject object) {
            this.url = url;
            this.text = text;
            this.object = object;
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
            cacheConfig(target, resolvePayload(target.getUrl(), reader.read(target.getUrl()), 0).text);
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
