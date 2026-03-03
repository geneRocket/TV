package com.fongmi.android.tv.bean;

import android.text.TextUtils;

import com.github.catvod.utils.Json;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Danmaku {

    @SerializedName("name")
    private String name;
    @SerializedName(value = "url", alternate = {"path"})
    private String url;
    @SerializedName("selected")
    private boolean selected;

    public static Danmaku create(String name, String url) {
        Danmaku item = new Danmaku();
        item.name = name;
        item.url = url;
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
        item.name = Json.safeString(object, "name");
        item.url = Json.safeString(object, "url");
        if (TextUtils.isEmpty(item.url)) item.url = Json.safeString(object, "path");
        String selected = Json.safeString(object, "selected");
        item.selected = "1".equals(selected) || "true".equalsIgnoreCase(selected);
        return item;
    }

    private static List<Danmaku> normalize(List<Danmaku> items) {
        List<Danmaku> result = new ArrayList<>();
        boolean hasSelected = false;
        for (Danmaku item : items) {
            if (item == null || item.isEmpty()) continue;
            if (item.selected) hasSelected = true;
            result.add(item);
        }
        if (!result.isEmpty() && !hasSelected) result.get(0).selected = true;
        return result;
    }

    public String getName() {
        return TextUtils.isEmpty(name) ? getDisplayName() : name;
    }

    public String getUrl() {
        return TextUtils.isEmpty(url) ? "" : url;
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
