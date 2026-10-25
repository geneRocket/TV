package com.fongmi.android.tv.api.config;
import com.fongmi.android.tv.utils.TextValues;
import com.fongmi.android.tv.bean.Config;
import com.github.catvod.utils.Json;
import com.google.gson.*;
import java.util.*;
/** Pure payload merge policy. Never publishes runtime state or writes configuration. */
final class ConfigMerger {
    static void vod(JsonObject target, JsonObject source, Config config) {
        JsonObject root = source.has("video") ? source.getAsJsonObject("video") : source;
        mergeSites(target, root, config);
        lives(target, source);
        if (root != source) lives(target, root);
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

    private static void mergeSites(JsonObject target, JsonObject source, Config config) {
        String spider = Json.safeString(source, "spider");
        List<JsonElement> sites = Json.safeListElement(source, "sites");
        if (!target.has("sites")) target.add("sites", new JsonArray());
        JsonArray targetSites = target.getAsJsonArray("sites");
        for (JsonElement element : sites) {
            JsonObject item = element.getAsJsonObject().deepCopy();
            String api = Json.safeString(item, "api");
            String jar = Json.safeString(item, "jar");
            String key = Json.safeString(item, "key");
            if (TextValues.isEmpty(jar) && api.startsWith("csp_")) item.addProperty("jar", spider);
            item.addProperty("key", VodConfig.siteKey(config.getId(), key));
            targetSites.add(item);
        }
    }

    private static void appendArray(JsonObject target, JsonObject source, String key) {
        if (!source.has(key) || source.get(key).isJsonNull()) return;
        if (!target.has(key)) target.add(key, new JsonArray());
        JsonArray targetArray = target.getAsJsonArray(key);
        for (JsonElement element : source.getAsJsonArray(key)) targetArray.add(element.deepCopy());
    }

    private static void copyIfEmpty(JsonObject target, JsonObject source, String key) {
        if (target.has(key)) return;
        if (source.has(key)) target.add(key, source.get(key).deepCopy());
    }

    static void lives(JsonObject target, JsonObject source) {
        if (!source.has("lives") || source.get("lives").isJsonNull()) return;
        String spider = Json.safeString(source, "spider");
        JsonArray lives = source.getAsJsonArray("lives");
        if (!target.has("lives")) target.add("lives", new JsonArray());
        JsonArray targetLives = target.getAsJsonArray("lives");
        for (JsonElement element : lives) {
            JsonObject item = element.getAsJsonObject().deepCopy();
            String api = Json.safeString(item, "api");
            String jar = Json.safeString(item, "jar");
            if (jar.isEmpty() && api.startsWith("csp_")) item.addProperty("jar", spider);
            targetLives.add(item);
        }
    }
    static void live(JsonObject target, JsonObject source) {
        lives(target, source);
        for (String key : new String[]{"rules","headers","proxy","hosts","ads"}) appendArray(target,source,key);
    }
}
