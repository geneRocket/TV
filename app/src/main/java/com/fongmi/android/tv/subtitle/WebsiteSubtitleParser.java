package com.fongmi.android.tv.subtitle;

import com.fongmi.android.tv.bean.Subtitle;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.*;

/** Subtitles.website response format; fallback provides one complete result page. */
public final class WebsiteSubtitleParser {
    public static final String BASE = "https://subtitles.website/strapi";
    private WebsiteSubtitleParser() {}

    public static SubtitleGateway.Page search(String json, String keyword) {
        JsonObject response = JsonParser.parseString(json).getAsJsonObject();
        List<Subtitle> items = new ArrayList<>();
        Set<String> urls = new HashSet<>();
        JsonElement data = response.get("data");
        if (data != null && data.isJsonArray()) for (JsonElement value : data.getAsJsonArray()) {
            if (!value.isJsonObject()) continue;
            JsonObject item = value.getAsJsonObject();
            String path = text(item, "download_url");
            if (path.isEmpty()) continue;
            String url = path.startsWith("http") ? path : BASE + path;
            if (!urls.add(url)) continue;
            String name = item.has("release") ? text(item, "release") : text(item, "film_title");
            if (name.isEmpty()) continue;
            JsonElement language = item.get("language");
            String label = language != null && language.isJsonObject() ? text(language.getAsJsonObject(), "name") : "";
            Subtitle subtitle = new Subtitle(name + (label.isEmpty() ? "" : " · " + label) + " · Subtitles.website", url, false);
            subtitle.setSource("Subtitles.website");
            subtitle.setLanguage(label);
            subtitle.setMovieTitle(text(item,"film_title"));
            items.add(subtitle);
        }
        items.sort(Comparator.comparingInt(item -> SubtitleRanking.score(item.getName(), keyword)));
        return new SubtitleGateway.Page(items, false, false, -1);
    }

    private static String text(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value == null || value.isJsonNull() ? "" : value.getAsString();
    }
}
