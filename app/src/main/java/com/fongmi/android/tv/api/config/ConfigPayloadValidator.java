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
        boolean found = arrays(root, VOD_ARRAYS);
        // Outer live sources are parsed too, even when the wrapped video already has sites.
        if (root != object) found = arrays(object, new String[]{"lives"}) || found;
        if (!found) throw invalid();
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
            boolean objects = key.equals("sites") || key.equals("lives") || key.equals("parses")
                    || key.equals("rules") || key.equals("doh") || key.equals("headers") || key.equals("proxy");
            for (JsonElement entry : value.getAsJsonArray()) {
                if (objects ? !entry.isJsonObject() : !entry.isJsonPrimitive()) throw invalid();
                if (key.equals("proxy")) validateProxy(entry.getAsJsonObject());
            }
            found = true;
        }
        return found;
    }

    private static void validateProxy(JsonObject rule) {
        for (String key : new String[]{"hosts", "urls"}) {
            JsonElement value = rule.get(key);
            if (value == null || value.isJsonNull()) continue;
            if (!value.isJsonArray()) throw invalid();
            for (JsonElement entry : value.getAsJsonArray()) {
                if (!entry.isJsonPrimitive() || !entry.getAsJsonPrimitive().isString()
                        || entry.getAsString().trim().isEmpty()) throw invalid();
                if (key.equals("urls")) {
                    try {
                        java.net.URI url = java.net.URI.create(entry.getAsString());
                        String authority = url.getRawAuthority();
                        if (authority == null) throw invalid();
                        int colon = authority.lastIndexOf(':');
                        if (colon > Math.max(authority.lastIndexOf('@'), authority.lastIndexOf(']'))) {
                            String port = authority.substring(colon + 1);
                            if (!port.matches("[0-9]+") || Long.parseLong(port) > 65535) throw invalid();
                        }
                    } catch (IllegalArgumentException error) {
                        throw invalid();
                    }
                }
            }
        }
    }

    private static IllegalStateException invalid() {
        return new IllegalStateException("Invalid configuration payload; keeping cached configuration");
    }
}
