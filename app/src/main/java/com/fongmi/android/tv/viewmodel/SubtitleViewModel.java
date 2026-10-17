package com.fongmi.android.tv.viewmodel;

import android.net.Uri;
import android.text.TextUtils;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.fongmi.android.tv.bean.Subtitle;
import com.fongmi.android.tv.bean.SubtitleData;
import com.fongmi.android.tv.utils.ThreadPools;
import com.github.catvod.net.OkHttp;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.Call;
import okhttp3.HttpUrl;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class SubtitleViewModel extends ViewModel {

    private static final String ASSRT_BASE_URL = "https://secure.assrt.net";
    private static final String ASSRT_DOWNLOAD_URL = "https://assrt.net";
    private static final String ASSRT_SEARCH_URL = ASSRT_BASE_URL + "/sub/";
    private static final String SUBTITLES_WEBSITE_API = "https://subtitles.website/strapi/api";
    private static final String SUBTITLES_WEBSITE_BASE = "https://subtitles.website/strapi";
    private static final String ASSRT_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/94.0.4606.54 Safari/537.36";
    private static final Pattern REGEX_SHOOTER_FILE_ONCLICK = Pattern.compile("onthefly\\(\"(\\d+)\",\"(\\d+)\",\"([\\s\\S]*)\"\\)");
    private static final Pattern REGEX_EPISODE = Pattern.compile("(?i)(?:[\\[（(]?(?:s\\d{1,2}e?\\d{0,3}|e\\d{1,3}|第\\s*\\d{1,4}\\s*[集话季]|\\d{1,4}\\s*[集话])[\\]）)]?|[\\s._-]+(?:1080|720|2160|4k|web[- ]?dl|bluray|x26[45]|h\\.?26[45]|hevc|aac|hdr|dv)[\\w.-]*)$");

    private final MutableLiveData<SubtitleData> searchResult;
    private final AtomicInteger requestSeq;
    private final AtomicInteger resolveSeq;
    private final OkHttpClient subtitleClient;
    private volatile Call currentSearchCall;
    private volatile Call currentFallbackSearchCall;
    private volatile Call currentResolveCall;

    public SubtitleViewModel() {
        searchResult = new MutableLiveData<>();
        requestSeq = new AtomicInteger();
        resolveSeq = new AtomicInteger();
        subtitleClient = OkHttp.client().newBuilder()
                .readTimeout(15, TimeUnit.SECONDS)
                .writeTimeout(15, TimeUnit.SECONDS)
                .connectTimeout(15, TimeUnit.SECONDS)
                .followRedirects(false)
                .followSslRedirects(false)
                .retryOnConnectionFailure(true)
                .build();
    }

    public LiveData<SubtitleData> searchResults() {
        return searchResult;
    }

    public void searchResult(String title, int page) {
        searchResultFromAssrt(title, page, false);
    }

    public void getSearchResultSubtitleUrls(Subtitle subtitle) {
        getSearchResultSubtitleUrlsFromAssrt(subtitle);
    }

    public void getSubtitleUrl(Subtitle subtitle, SubtitleLoader subtitleLoader) {
        getSubtitleUrlFromAssrt(subtitle, subtitleLoader);
    }

    private void setSearchListData(List<Subtitle> data, boolean isNew, boolean isZip) {
        setSearchListData(data, isNew, isZip, false);
    }

    private void setSearchListData(List<Subtitle> data, boolean isNew, boolean isZip, boolean hasNext) {
        try {
            SubtitleData subtitleData = new SubtitleData();
            subtitleData.setSubtitleList(data);
            subtitleData.setIsNew(isNew);
            subtitleData.setIsZip(isZip);
            subtitleData.setHasNext(hasNext);
            searchResult.postValue(subtitleData);
        } catch (Throwable e) {
            ThreadPools.log(e, "Subtitle data update failed.");
            searchResult.postValue(null);
        }
    }

    private int pagesTotal = -1;

    private void searchResultFromAssrt(String title, int page, boolean simplified) {
        searchResultFromAssrt(title, page, simplified, -1);
    }

    static int nextSearchRequest(AtomicInteger sequence, int expected) {
        if (expected < 0) return sequence.incrementAndGet();
        return sequence.compareAndSet(expected, expected + 1) ? expected + 1 : -1;
    }

    private synchronized void searchResultFromAssrt(String title, int page, boolean simplified, int expected) {
        try {
            int seq = nextSearchRequest(requestSeq, expected);
            if (seq < 0) return;
            int currentPage = Math.max(1, page);
            String keyword = title == null ? "" : title.trim();
            cancelCall(currentSearchCall);
            cancelCall(currentFallbackSearchCall);
            if (TextUtils.isEmpty(keyword)) {
                if (seq == requestSeq.get()) setSearchListData(new ArrayList<>(), true, true);
                return;
            }
            if (pagesTotal > 0 && currentPage > pagesTotal) {
                if (seq == requestSeq.get()) setSearchListData(new ArrayList<>(), currentPage <= 1, true);
                return;
            }
            if (currentPage == 1) pagesTotal = -1;//第一页时 重置页大小
            String url = ASSRT_SEARCH_URL + "?searchword=" + encode(keyword) + "&sort=rank&page=" + currentPage + "&no_redir=1";

            Call call = subtitleClient.newCall(assrtRequest(url, ASSRT_BASE_URL));
            currentSearchCall = call;
            call.enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    if (call.isCanceled()) return;
                    ThreadPools.log(e, "Subtitle search failed.");
                    if (seq == requestSeq.get()) searchResultFromSubtitlesWebsite(keyword, currentPage, seq);
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    try (Response res = response; ResponseBody body = res.body()) {
                        if (!res.isSuccessful() || body == null) {
                            if (seq == requestSeq.get()) searchResultFromSubtitlesWebsite(keyword, currentPage, seq);
                            return;
                        }
                        String content = body.string();
                        Document doc = Jsoup.parse(content);
                        List<Subtitle> data = parseSearchResults(doc, keyword);
                        if (seq != requestSeq.get()) return;
                        if (data.isEmpty() && currentPage == 1) {
                            String fallback = simplifyKeyword(keyword);
                            if (!simplified && !TextUtils.equals(keyword, fallback)) {
                                searchResultFromAssrt(fallback, currentPage, true, seq);
                                return;
                            }
                            searchResultFromSubtitlesWebsite(fallback, currentPage, seq);
                            return;
                        }
                        pagesTotal = parsePagesTotal(doc);
                        setSearchListData(data, currentPage <= 1, true, hasNextPage(doc, currentPage));
                    } catch (Throwable th) {
                        ThreadPools.log(th, "Subtitle search parse failed.");
                        if (seq == requestSeq.get()) searchResultFromSubtitlesWebsite(keyword, currentPage, seq);
                    }
                }
            });
        } catch (Exception e) {
            ThreadPools.log(e, "Subtitle search init failed.");
        }
    }

    private synchronized void searchResultFromSubtitlesWebsite(String title, int page, int seq) {
        if (seq != requestSeq.get()) return;
        String keyword = title == null ? "" : title.trim();
        if (TextUtils.isEmpty(keyword)) {
            setSearchListData(new ArrayList<>(), true, false);
            return;
        }
        String url = SUBTITLES_WEBSITE_API + "/search/subtitles?title=" + encode(keyword) + "&format=srt";
        Call call = subtitleClient.newCall(request(url, SUBTITLES_WEBSITE_BASE));
        currentFallbackSearchCall = call;
        call.enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                if (call.isCanceled()) return;
                ThreadPools.log(e, "Fallback subtitle search failed.");
                if (seq == requestSeq.get()) setSearchListData(null, page <= 1, false);
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try (Response res = response; ResponseBody body = res.body()) {
                    if (!res.isSuccessful() || body == null) {
                        if (seq == requestSeq.get()) setSearchListData(null, page <= 1, false);
                        return;
                    }
                    List<Subtitle> data = parseSubtitlesWebsiteResults(new JSONObject(body.string()), keyword);
                    if (seq == requestSeq.get()) setSearchListData(data, true, false, false);
                } catch (Throwable th) {
                    ThreadPools.log(th, "Fallback subtitle search parse failed.");
                    if (seq == requestSeq.get()) setSearchListData(null, page <= 1, false);
                }
            }
        });
    }

    private List<Subtitle> parseSubtitlesWebsiteResults(JSONObject response, String keyword) {
        List<Subtitle> data = new ArrayList<>();
        Set<String> urls = new HashSet<>();
        JSONArray items = response.optJSONArray("data");
        if (items == null) return data;
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.optJSONObject(i);
            if (item == null) continue;
            String path = item.optString("download_url");
            if (TextUtils.isEmpty(path)) continue;
            String url = path.startsWith("http") ? path : SUBTITLES_WEBSITE_BASE + path;
            if (!urls.add(url)) continue;
            JSONObject language = item.optJSONObject("language");
            String languageName = language == null ? "" : language.optString("name");
            String name = item.optString("release", item.optString("film_title"));
            if (TextUtils.isEmpty(name)) continue;
            Subtitle subtitle = new Subtitle();
            subtitle.setName(name + (TextUtils.isEmpty(languageName) ? "" : " · " + languageName) + " · Subtitles.website");
            subtitle.setUrl(url);
            subtitle.setIsZip(false);
            subtitle.setSource("Subtitles.website");
            data.add(subtitle);
        }
        Collections.sort(data, Comparator.comparingInt(item -> subtitleScore(item.getName(), keyword)));
        return data;
    }

    private List<Subtitle> parseSearchResults(Document doc, String keyword) {
        Elements items = doc.select(".resultcard .sublist_box_title a.introtitle, .resultcard a.introtitle, a.introtitle");
        List<Subtitle> data = new ArrayList<>();
        Set<String> urls = new HashSet<>();
        for (Element item : items) {
            String href = item.attr("href");
            if (TextUtils.isEmpty(href)) continue;
            String url = assrtUrl(ASSRT_DOWNLOAD_URL, href);
            if (!urls.add(url)) continue;
            String name = item.attr("title");
            if (TextUtils.isEmpty(name)) name = item.text();
            if (TextUtils.isEmpty(name)) continue;
            Subtitle one = new Subtitle();
            one.setName(name.trim());
            one.setUrl(url);
            one.setIsZip(true);
            one.setSource("ASSRT");
            data.add(one);
        }
        Collections.sort(data, Comparator.comparingInt(item -> subtitleScore(item.getName(), keyword)));
        return data;
    }

    private int parsePagesTotal(Document doc) {
        Elements pages = doc.select(".pagelinkcard a, .pagination a");
        for (int i = pages.size() - 1; i >= 0; i--) {
            String[] values = pages.get(i).text().split("/", 2);
            if (values.length != 2) continue;
            try {
                return Integer.parseInt(values[1].trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return -1;
    }

    private boolean hasNextPage(Document doc, int currentPage) {
        if (pagesTotal > 0) return currentPage < pagesTotal;
        for (Element item : doc.select(".pagelinkcard a, .pagination a")) {
            String text = item.text().trim();
            if (text.contains("下一") || text.equalsIgnoreCase("next") || text.equals(">")) return !item.hasClass("disabled");
        }
        return false;
    }

    private String simplifyKeyword(String keyword) {
        String result = keyword == null ? "" : keyword.trim();
        String previous;
        do {
            previous = result;
            result = REGEX_EPISODE.matcher(result).replaceFirst("").trim();
        } while (!TextUtils.equals(previous, result));
        return result.replaceAll("[\\s._-]+$", "").trim();
    }

    private int subtitleScore(String name, String keyword) {
        String title = normalizeForMatch(name);
        String query = normalizeForMatch(keyword);
        if (title.equals(query)) return 0;
        if (title.startsWith(query)) return 1;
        if (title.contains(query)) return 2;
        String simplified = normalizeForMatch(simplifyKeyword(keyword));
        return !TextUtils.isEmpty(simplified) && title.contains(simplified) ? 3 : 4;
    }

    private String normalizeForMatch(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("[\\s._\\-\\[\\]（）()]+", "");
    }

    private synchronized void getSearchResultSubtitleUrlsFromAssrt(Subtitle subtitle) {
        try {
            int seq = requestSeq.incrementAndGet();
            cancelCall(currentSearchCall);
            if (subtitle == null || TextUtils.isEmpty(subtitle.getUrl())) {
                if (seq == requestSeq.get()) setSearchListData(null, true, true);
                return;
            }
            String url = subtitle.getUrl();
            Call call = subtitleClient.newCall(assrtRequest(url, ASSRT_DOWNLOAD_URL));
            currentSearchCall = call;
            call.enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    if (call.isCanceled()) return;
                    ThreadPools.log(e, "Subtitle detail search failed.");
                    if (seq == requestSeq.get()) setSearchListData(null, true, true);
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    try (Response res = response; ResponseBody body = res.body()) {
                        if (!res.isSuccessful() || body == null) {
                            if (seq == requestSeq.get()) setSearchListData(null, true, false);
                            return;
                        }
                        String content = body.string();
                        List<Subtitle> data = new ArrayList<>();
                        Document doc = Jsoup.parse(content);
                        Elements items = doc.select("#detail-filelist .waves-effect");
                        if (items.size() > 0) {//压缩包里面的字幕
                            for (Element item : items) {
                                String onclick = item.attr("onclick");
                                if (TextUtils.isEmpty(onclick)) continue;
                                Matcher matcher = REGEX_SHOOTER_FILE_ONCLICK.matcher(onclick);
                                if (matcher.find()) {
                                    String url = String.format(ASSRT_BASE_URL + "/download/%s/-/%s/%s", matcher.group(1), matcher.group(2), Uri.encode(matcher.group(3)));
                                    Subtitle one = new Subtitle();
                                    Element name = item.selectFirst("#filelist-name");
                                    one.setName(name == null ? matcher.group(3) : name.text());
                                    one.setUrl(url);
                                    one.setIsZip(false);
                                    data.add(one);
                                }
                            }
                            if (seq == requestSeq.get()) setSearchListData(data, true, false);
                        } else {//有的字幕 不一定是压缩包
                            Element item = doc.selectFirst(".download a#btn_download");
                            if (item == null) {
                                if (seq == requestSeq.get()) setSearchListData(null, true, false);
                                return;
                            }
                            String href = item.attr("href");
                            if (TextUtils.isEmpty(href)) {
                                if (seq == requestSeq.get()) setSearchListData(null, true, false);
                                return;
                            }
                            String h2 = href.toLowerCase();
                            if (h2.endsWith("srt") || h2.endsWith("ass") || h2.endsWith("scc") || h2.endsWith("ttml")) {
                                String url = assrtUrl(ASSRT_DOWNLOAD_URL, href);
                                Subtitle one = new Subtitle();
                                String title = href.substring(href.lastIndexOf("/") + 1);
                                try {
                                    one.setName(URLDecoder.decode(title, StandardCharsets.UTF_8.name()));
                                } catch (IllegalArgumentException e) {
                                    one.setName(title);
                                }
                                one.setUrl(url);
                                one.setIsZip(false);
                                data.add(one);
                                if (seq == requestSeq.get()) setSearchListData(data, true, false);
                            } else {
                                if (seq == requestSeq.get()) setSearchListData(null, true, false);
                            }
                        }
                    } catch (Throwable th) {
                        ThreadPools.log(th, "Subtitle detail parse failed.");
                        if (seq == requestSeq.get()) setSearchListData(null, true, false);
                    }
                }
            });
        } catch (Exception e) {
            ThreadPools.log(e, "Subtitle detail init failed.");
        }
    }

    private void getSubtitleUrlFromAssrt(Subtitle subtitle, SubtitleLoader subtitleLoader) {
        int seq = resolveSeq.incrementAndGet();
        cancelCall(currentResolveCall);
        if (subtitle == null || TextUtils.isEmpty(subtitle.getUrl()) || subtitleLoader == null) return;
        Request request = assrtRequest(subtitle.getUrl(), ASSRT_BASE_URL);
        Call call = subtitleClient.newCall(request);
        currentResolveCall = call;
        call.enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                if (call.isCanceled()) return;
                ThreadPools.log(e, "Subtitle resolve failed.");
                if (seq == resolveSeq.get()) subtitleLoader.loadSubtitle(subtitle);
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try (Response res = response) {
                    if (seq != resolveSeq.get()) return;
                    String location = normalizeAssrtLocation(res.request().url().toString(), res.header("location"));
                    subtitle.setUrl(TextUtils.isEmpty(location) ? subtitle.getUrl() : location);
                    if (seq != resolveSeq.get()) return;
                    subtitleLoader.loadSubtitle(subtitle);
                }
            }
        });
    }

    private Request assrtRequest(String url, String referer) {
        return request(url, referer);
    }

    private Request request(String url, String referer) {
        return new Request.Builder()
                .url(url)
                .get()
                .addHeader("Referer", referer)
                .addHeader("User-Agent", ASSRT_UA)
                .build();
    }

    private String assrtUrl(String base, String href) {
        if (TextUtils.isEmpty(href)) return "";
        if (href.startsWith("http://") || href.startsWith("https://")) return href;
        if (href.startsWith("//")) return "https:" + href;
        if (href.startsWith("/")) return base + href;
        return base + "/" + href;
    }

    static String normalizeAssrtLocation(String requestUrl, String location) {
        if (requestUrl == null || location == null || location.trim().isEmpty()) return "";
        HttpUrl base = HttpUrl.parse(requestUrl);
        HttpUrl target = base == null ? null : base.resolve(location.trim());
        return target == null ? "" : target.toString();
    }

    private String encode(String value) {
        try {
            return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8.name());
        } catch (Exception e) {
            ThreadPools.log(e, "Subtitle query encode failed.");
            return "";
        }
    }

    @Override
    protected synchronized void onCleared() {
        requestSeq.incrementAndGet();
        resolveSeq.incrementAndGet();
        cancelCall(currentSearchCall);
        cancelCall(currentFallbackSearchCall);
        cancelCall(currentResolveCall);
        super.onCleared();
    }

    private void cancelCall(Call call) {
        if (call != null) call.cancel();
    }

    public interface SubtitleLoader {
        void loadSubtitle(Subtitle subtitle);
    }
}
