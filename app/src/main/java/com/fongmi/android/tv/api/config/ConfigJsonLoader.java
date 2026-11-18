package com.fongmi.android.tv.api.config;

import com.fongmi.android.tv.bean.Depot;
import com.fongmi.android.tv.utils.ThreadPools;
import com.github.catvod.utils.Json;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Recursive depot fetching with injected I/O and validation; no App or runtime-state lookup. */
final class ConfigJsonLoader {
    interface Reader { String read(String url) throws Throwable; }
    interface Source<T> { T read(String url) throws Throwable; }
    private final Reader reader;
    private final Consumer<JsonObject> validator;
    private final Consumer<Depot> register;

    ConfigJsonLoader(Reader reader, Consumer<JsonObject> validator, Consumer<Depot> register) {
        this.reader = reader;
        this.validator = validator;
        this.register = register;
    }

    JsonObject load(String url, int depth) throws Throwable {
        ConfigLoadTask.check();
        return resolve(JsonParser.parseString(reader.read(url)).getAsJsonObject(), depth);
    }

    JsonObject resolve(JsonObject object, int depth) throws Throwable {
        ConfigLoadTask.check();
        requireDepth(depth);
        if (object.has("msg")) throw new IllegalStateException(object.get("msg").getAsString());
        if (!object.has("urls")) {
            validator.accept(object);
            return object;
        }
        return resolveDepot(object, register, url -> load(url, depth + 1));
    }

    static void requireDepth(int depth) {
        if (depth > 5) throw new IllegalStateException("Too many redirects.");
    }

    static List<Depot> entries(JsonObject object) {
        List<Depot> sources = new ArrayList<>();
        JsonArray entries = object.getAsJsonArray("urls");
        for (JsonElement entry : entries) {
            if (!entry.isJsonObject()) continue;
            JsonObject item = entry.getAsJsonObject();
            String url = Json.safeString(item, "url");
            if (!url.isEmpty()) sources.add(new Depot(url, Json.safeString(item, "name")));
        }
        return sources;
    }

    static <T> T resolveDepot(JsonObject object, Consumer<Depot> register, Source<T> source) throws Throwable {
        List<Depot> entries = entries(object);
        ConfigLoadTask.commit(() -> entries.forEach(register));
        Throwable failure = null;
        for (Depot entry : entries) {
            try {
                return source.read(entry.getUrl());
            } catch (Throwable error) {
                ConfigLoadTask.check();
                failure = error;
                ThreadPools.log(error, "Config depot source failed.");
            }
        }
        if (failure != null) throw failure;
        throw new IllegalStateException("No valid config");
    }
}
