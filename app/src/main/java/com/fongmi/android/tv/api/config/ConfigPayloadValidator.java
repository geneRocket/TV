package com.fongmi.android.tv.api.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/** Validate payload shape before replacing a working cached configuration. */
final class ConfigPayloadValidator {
    private static final String[] VOD_ARRAYS = {"sites", "lives", "parses", "rules", "flags", "ads", "headers", "proxy", "hosts", "doh"};
    private static final String[] LIVE_ARRAYS = {"lives", "rules", "ads", "headers", "proxy", "hosts"};

    static void cachedVod(JsonObject object) {
        if (!depot(object)) vod(object);
    }

    static void cachedLive(JsonObject object) {
        if (!depot(object)) live(object);
    }

    private static boolean depot(JsonObject object) {
        rejectMessage(object);
        if (!object.has("urls")) return false;
        JsonElement urls = object.get("urls");
        if (!urls.isJsonArray() || urls.getAsJsonArray().isEmpty()) throw invalid();
        for (JsonElement entry : urls.getAsJsonArray()) {
            if (!entry.isJsonObject()) throw invalid();
            JsonElement url = entry.getAsJsonObject().get("url");
            if (url == null || !url.isJsonPrimitive() || !url.getAsJsonPrimitive().isString()
                    || url.getAsString().trim().isEmpty()) throw invalid();
        }
        return true;
    }

    static void vod(JsonObject object) {
        rejectMessage(object);
        JsonObject root = object;
        if (object.has("video")) {
            if (!object.get("video").isJsonObject()) throw invalid();
            root = object.getAsJsonObject("video");
            rejectMessage(root);
        }
        if (!arrays(root, VOD_ARRAYS) && !(root != object && arrays(object, new String[]{"lives"}))) throw invalid();
    }

    static void live(JsonObject object) {
        rejectMessage(object);
        if (!arrays(object, LIVE_ARRAYS)) throw invalid();
    }

    private static void rejectMessage(JsonObject object) {
        if (object.has("msg")) throw invalid();
    }

    private static boolean arrays(JsonObject object, String[] keys) {
        boolean found = false;
        for (String key : keys) {
            JsonElement value = object.get(key);
            if (value == null || value.isJsonNull()) continue;
            if (!value.isJsonArray()) throw invalid();
            if (key.equals("sites") || key.equals("lives") || key.equals("parses")) {
                for (JsonElement entry : value.getAsJsonArray()) if (!entry.isJsonObject()) throw invalid();
            }
            found = true;
        }
        return found;
    }

    private static IllegalStateException invalid() {
        return new IllegalStateException("Invalid configuration payload; keeping cached configuration");
    }
}
