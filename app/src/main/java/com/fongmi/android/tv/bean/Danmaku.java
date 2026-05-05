package com.fongmi.android.tv.bean;

import android.text.TextUtils;

import com.github.catvod.utils.Json;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class Danmaku {

    @SerializedName("name")
    private String name;
    @SerializedName(value = "url", alternate = {"path"})
    private String url;
    @SerializedName("selected")
    private boolean selected;

    public static Danmaku create(String name, String url) {
        Danmaku item = new Danmaku();
        item.name = clean(name);
        item.url = clean(url);
        return item;
    }

    public static Danmaku from(String url) {
        return create("", url);
    }

    public static Danmaku empty() {
        return create("", "");
    }

    public static List<Danmaku> arrayFrom(String value) {
        if (TextUtils.isEmpty(value)) return Collections.emptyList();
        String text = value.trim();
        try {
            JsonElement element = Json.parse(text);
            if (element.isJsonPrimitive()) return normalize(new ArrayList<>(Collections.singletonList(create("", element.getAsString()))));
            if (element.isJsonArray()) return fromArray(element.getAsJsonArray());
            if (element.isJsonObject()) return fromObject(element.getAsJsonObject());
        } catch (Exception ignored) {
        }
        return normalize(new ArrayList<>(Collections.singletonList(create("", value))));
    }

    private static List<Danmaku> fromArray(JsonArray array) {
        List<Danmaku> items = new ArrayList<>();
        for (JsonElement item : array) {
            if (item == null || item.isJsonNull()) continue;
            if (item.isJsonPrimitive()) items.add(create("", item.getAsString()));
            else if (item.isJsonObject()) items.add(from(item.getAsJsonObject()));
        }
        return normalize(items);
    }

    private static List<Danmaku> fromObject(JsonObject object) {
        if (object.has("url") || object.has("path")) return normalize(new ArrayList<>(Collections.singletonList(from(object))));
        if (object.has("urls") && object.get("urls").isJsonArray()) return fromArray(object.getAsJsonArray("urls"));
        if (object.has("list") && object.get("list").isJsonArray()) return fromArray(object.getAsJsonArray("list"));
        return Collections.emptyList();
    }

    private static Danmaku from(JsonObject object) {
        Danmaku item = new Danmaku();
        item.name = clean(Json.safeString(object, "name"));
        item.url = withHeaders(clean(Json.safeString(object, "url")), object);
        if (TextUtils.isEmpty(item.url)) item.url = withHeaders(clean(Json.safeString(object, "path")), object);
        String selected = Json.safeString(object, "selected");
        item.selected = "1".equals(selected) || "true".equalsIgnoreCase(selected);
        return item;
    }

    private static String withHeaders(String url, JsonObject object) {
        if (TextUtils.isEmpty(url)) return "";
        StringBuilder builder = new StringBuilder(url);
        appendHeaders(builder, object);
        appendText(builder, object, "cookie", "@Cookie=");
        appendText(builder, object, "Cookie", "@Cookie=");
        appendText(builder, object, "referer", "@Referer=");
        appendText(builder, object, "ref", "@Referer=");
        appendText(builder, object, "ua", "@User-Agent=");
        appendText(builder, object, "userAgent", "@User-Agent=");
        appendText(builder, object, "User-Agent", "@User-Agent=");
        return builder.toString();
    }

    private static void appendHeaders(StringBuilder builder, JsonObject object) {
        JsonObject headers = new JsonObject();
        putHeaders(headers, object.get("header"));
        putHeaders(headers, object.get("headers"));
        if (!headers.entrySet().isEmpty()) builder.append("@Headers=").append(headers);
    }

    private static void putHeaders(JsonObject headers, JsonElement element) {
        if (element == null || !element.isJsonObject()) return;
        for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
            String value = clean(Json.safeString(element.getAsJsonObject(), entry.getKey()));
            if (!TextUtils.isEmpty(entry.getKey()) && !TextUtils.isEmpty(value)) headers.addProperty(entry.getKey(), value);
        }
    }

    private static void appendText(StringBuilder builder, JsonObject object, String key, String tag) {
        String value = clean(Json.safeString(object, key));
        if (TextUtils.isEmpty(value)) return;
        builder.append(tag).append(value);
    }

    private static List<Danmaku> normalize(List<Danmaku> items) {
        Map<String, Danmaku> deduped = new LinkedHashMap<>();
        boolean hasSelected = false;
        for (Danmaku item : items) {
            if (item == null || item.isEmpty()) continue;
            Danmaku source = deduped.get(item.getUrl());
            if (source == null) {
                deduped.put(item.getUrl(), item);
                source = item;
            }
            if (item.selected) {
                source.selected = true;
                hasSelected = true;
            }
        }
        List<Danmaku> result = new ArrayList<>(deduped.values());
        hasSelected = false;
        for (Danmaku item : result) {
            if (!item.selected) continue;
            if (hasSelected) item.selected = false;
            else hasSelected = true;
        }
        if (!result.isEmpty() && !hasSelected) result.get(0).selected = true;
        return result;
    }

    private static String clean(String value) {
        return TextUtils.isEmpty(value) ? "" : value.trim();
    }

    public String getName() {
        return TextUtils.isEmpty(clean(name)) ? getDisplayName() : clean(name);
    }

    public String getUrl() {
        return clean(url);
    }

    public boolean isSelected() {
        return selected;
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
    }

    public boolean isEmpty() {
        return getUrl().isEmpty();
    }

    public String getDisplayName() {
        String value = getUrl();
        int index = Math.max(value.lastIndexOf('/'), value.lastIndexOf('\\'));
        if (index >= 0 && index < value.length() - 1) return value.substring(index + 1);
        return "Danmaku";
    }
}
