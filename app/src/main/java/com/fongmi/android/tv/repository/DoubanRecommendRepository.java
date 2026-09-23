package com.fongmi.android.tv.repository;

import android.text.TextUtils;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.bean.Vod;
import com.github.catvod.net.OkHttp;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.Call;
import okhttp3.Headers;
import com.fongmi.android.tv.utils.DeferredTask;
import okhttp3.HttpUrl;
import okhttp3.Request;
import okhttp3.Response;

public final class DoubanRecommendRepository {

    private static final String USER_AGENT = "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 Mobile Safari/537.36";
    private static final Pattern YEAR = Pattern.compile("(?:19|20)\\d{2}");
    private static final Pattern QUALITY = Pattern.compile("(?i)(?:\\s|[-_])*(?:4k|8k|1080p|2160p|720p|超清|高清|蓝光|原盘|完整版)$");
    private static final Map<String, List<Vod>> CACHE = Collections.synchronizedMap(new LinkedHashMap<String, List<Vod>>(48, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, List<Vod>> eldest) {
            return size() > 48;
        }
    });
    private static final int TIMEOUT_MS = 8000;
    private static final int LIMIT = 10;

    private DoubanRecommendRepository() {
    }

    public static DeferredTask.Work<List<Vod>> request(String title, String year) {
        return new RecommendationRequest(title, year);
    }

    private static final class RecommendationRequest implements DeferredTask.Work<List<Vod>> {
        private final String title;
        private final String year;
        private volatile boolean cancelled;
        private Call activeCall;

        RecommendationRequest(String title, String year) {
            this.title = title;
            this.year = year;
        }

        @Override public synchronized void cancel() {
            cancelled = true;
            if (activeCall != null) activeCall.cancel();
        }

        @Override public List<Vod> call() {
            String normalized = normalize(title);
            if (normalized.isEmpty()) return Collections.emptyList();
            String key = normalized + "@" + safeYear(year);
            List<Vod> cached = CACHE.get(key);
            if (cached != null) return cached;
            List<Vod> result = request(title, year, normalized);
            if (!cancelled && !result.isEmpty()) CACHE.put(key, result);
            return result;
        }

        private List<Vod> request(String title, String year, String normalized) {
            try {
                String subjectId = findSubject(title, year, normalized);
                if (subjectId.isEmpty()) return Collections.emptyList();
                return recommendations(subjectId, normalized);
            } catch (Throwable ignored) {
                return Collections.emptyList();
            }
        }

        private String findSubject(String title, String year, String normalized) throws IOException {
            HttpUrl url = new HttpUrl.Builder().scheme("https").host("movie.douban.com")
                    .addPathSegments("j/subject_suggest").addQueryParameter("q", title).build();
            JsonArray array = App.gson().fromJson(get(url, "https://movie.douban.com/"), JsonArray.class);
            if (array == null) return "";
            String expectedYear = safeYear(year);
            String fallback = "";
            for (JsonElement element : array) {
                if (!element.isJsonObject()) continue;
                JsonObject item = element.getAsJsonObject();
                if (!"movie".equals(string(item, "type"))) continue;
                if (!normalized.equals(normalize(string(item, "title")))) continue;
                String id = string(item, "id");
                if (id.isEmpty()) continue;
                if (fallback.isEmpty()) fallback = id;
                if (!expectedYear.isEmpty() && expectedYear.equals(safeYear(string(item, "year")))) return id;
            }
            return fallback;
        }

        private List<Vod> recommendations(String subjectId, String currentTitle) throws IOException {
            HttpUrl url = new HttpUrl.Builder().scheme("https").host("m.douban.com")
                    .addPathSegments("rexxar/api/v2/movie/" + subjectId + "/recommendations")
                    .addQueryParameter("refresh", "0").addQueryParameter("start", "0")
                    .addQueryParameter("count", String.valueOf(LIMIT + 2)).build();
            String referer = "https://m.douban.com/movie/subject/" + subjectId + "/";
            JsonArray array = App.gson().fromJson(get(url, referer), JsonArray.class);
            if (array == null) return Collections.emptyList();
            List<Vod> result = new ArrayList<>();
            for (JsonElement element : array) {
                if (!element.isJsonObject() || result.size() >= LIMIT) break;
                JsonObject item = element.getAsJsonObject();
                String title = string(item, "title");
                if (title.isEmpty() || currentTitle.equals(normalize(title))) continue;
                Vod vod = new Vod();
                vod.setVodId(string(item, "id"));
                vod.setVodName(title);
                vod.setVodPic(image(item));
                vod.setVodYear(safeYear(string(item, "card_subtitle")));
                vod.setVodRemarks(rating(item));
                result.add(vod);
            }
            return Collections.unmodifiableList(result);
        }

        private String get(HttpUrl url, String referer) throws IOException {
            Headers headers = new Headers.Builder().add("User-Agent", USER_AGENT).add("Referer", referer)
                    .add("Accept", "application/json, text/plain, */*").build();
            Request request = new Request.Builder().url(url).headers(headers).build();
            Call call = OkHttp.client(TIMEOUT_MS).newCall(request);
            synchronized (this) {
                if (cancelled || Thread.currentThread().isInterrupted()) throw new IOException("Recommendation cancelled");
                activeCall = call;
            }
            try (Response response = call.execute()) {
                if (!response.isSuccessful() || response.body() == null) return "";
                return response.body().string();
            } finally {
                synchronized (this) { if (activeCall == call) activeCall = null; }
            }
        }

    }

    private static String image(JsonObject item) {
        if (!item.has("pic") || !item.get("pic").isJsonObject()) return "";
        JsonObject pic = item.getAsJsonObject("pic");
        String normal = string(pic, "normal");
        String url = normal.isEmpty() ? string(pic, "large") : normal;
        if (url.isEmpty()) return "";
        // Douban's image CDN returns 418 for hotlink requests without these headers.
        return url + "@Referer=https://movie.douban.com/@User-Agent=" + USER_AGENT;
    }

    private static String rating(JsonObject item) {
        if (!item.has("rating") || !item.get("rating").isJsonObject()) return "";
        JsonElement value = item.getAsJsonObject("rating").get("value");
        if (value == null || value.isJsonNull()) return "";
        double score = value.getAsDouble();
        return score <= 0 ? "" : String.format(Locale.US, "%.1f", score);
    }

    private static String string(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value == null || value.isJsonNull() ? "" : value.getAsString().trim();
    }

    private static String safeYear(String value) {
        if (TextUtils.isEmpty(value)) return "";
        Matcher matcher = YEAR.matcher(value);
        return matcher.find() ? matcher.group() : "";
    }

    static String normalize(String value) {
        if (TextUtils.isEmpty(value)) return "";
        String result = value.trim();
        while (QUALITY.matcher(result).find()) result = QUALITY.matcher(result).replaceFirst("").trim();
        return result.toLowerCase(Locale.ROOT).replaceAll("[\\p{P}\\p{Z}\\s]+", "");
    }
}
