package com.fongmi.android.tv.viewmodel;

import android.net.Uri;
import android.text.TextUtils;

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

import java.io.IOException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class SubtitleViewModel extends ViewModel {

    private static final String ASSRT_BASE_URL = "https://secure.assrt.net";
    private static final String ASSRT_DOWNLOAD_URL = "https://assrt.net";
    private static final String ASSRT_SEARCH_URL = ASSRT_BASE_URL + "/sub/";
    private static final String ASSRT_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/94.0.4606.54 Safari/537.36";
    private static final Pattern REGEX_SHOOTER_FILE_ONCLICK = Pattern.compile("onthefly\\(\"(\\d+)\",\"(\\d+)\",\"([\\s\\S]*)\"\\)");

    public MutableLiveData<SubtitleData> searchResult;
    private final AtomicInteger requestSeq;
    private final AtomicInteger resolveSeq;
    private final OkHttpClient subtitleClient;
    private volatile Call currentSearchCall;
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

    public void searchResult(String title, int page) {
        searchResultFromAssrt(title, page);
    }

    public void getSearchResultSubtitleUrls(Subtitle subtitle) {
        getSearchResultSubtitleUrlsFromAssrt(subtitle);
    }

    public void getSubtitleUrl(Subtitle subtitle, SubtitleLoader subtitleLoader) {
        getSubtitleUrlFromAssrt(subtitle, subtitleLoader);
    }

    private void setSearchListData(List<Subtitle> data, boolean isNew, boolean isZip) {
        try {
            SubtitleData subtitleData = new SubtitleData();
            subtitleData.setSubtitleList(data);
            subtitleData.setIsNew(isNew);
            subtitleData.setIsZip(isZip);
            searchResult.postValue(subtitleData);
        } catch (Throwable e) {
            ThreadPools.log(e, "Subtitle data update failed.");
            searchResult.postValue(null);
        }
    }

    private int pagesTotal = -1;

    private void searchResultFromAssrt(String title, int page) {
        try {
            int seq = requestSeq.incrementAndGet();
            int currentPage = Math.max(1, page);
            String keyword = title == null ? "" : title.trim();
            cancelCall(currentSearchCall);
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
                    if (seq == requestSeq.get()) setSearchListData(null, currentPage <= 1, true);
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    try (Response res = response; ResponseBody body = res.body()) {
                        if (!res.isSuccessful() || body == null) {
                            if (seq == requestSeq.get()) setSearchListData(null, currentPage <= 1, true);
                            return;
                        }
                        String content = body.string();
                        Document doc = Jsoup.parse(content);
                        Elements items = doc.select(".resultcard .sublist_box_title a.introtitle");
                        List<Subtitle> data = new ArrayList<>();
                        for (Element item : items) {
                            String title = item.attr("title");
                            String href = item.attr("href");
                            if (TextUtils.isEmpty(href)) continue;
                            Subtitle one = new Subtitle();
                            one.setName(title);
                            one.setUrl(assrtUrl(ASSRT_DOWNLOAD_URL, href));
                            one.setIsZip(true);
                            data.add(one);
                        }
                        if (seq != requestSeq.get()) return;
                        setSearchListData(data, currentPage <= 1, true);
                        Elements pages = doc.select(".pagelinkcard a");
                        if (pages.size() > 0) {
                            String[] ps = pages.last().text().split("/", 2);
                            if (ps.length == 2 && !TextUtils.isEmpty(ps[1])) {
                                try {
                                    pagesTotal = Integer.parseInt(ps[1].trim());
                                } catch (NumberFormatException e) {
                                    pagesTotal = -1;
                                }
                            }
                        }
                    } catch (Throwable th) {
                        ThreadPools.log(th, "Subtitle search parse failed.");
                        if (seq == requestSeq.get()) setSearchListData(null, currentPage <= 1, true);
                    }
                }
            });
        } catch (Exception e) {
            ThreadPools.log(e, "Subtitle search init failed.");
        }
    }

    private void getSearchResultSubtitleUrlsFromAssrt(Subtitle subtitle) {
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
                    String location = normalizeAssrtLocation(res.header("location"));
                    subtitle.setUrl(TextUtils.isEmpty(location) ? subtitle.getUrl() : location);
                    if (seq != resolveSeq.get()) return;
                    subtitleLoader.loadSubtitle(subtitle);
                }
            }
        });
    }

    private Request assrtRequest(String url, String referer) {
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

    private String normalizeAssrtLocation(String location) {
        if (TextUtils.isEmpty(location)) return "";
        String url = assrtUrl(ASSRT_BASE_URL, location.trim());
        return url.startsWith("http://") || url.startsWith("https://") ? url : "";
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
    protected void onCleared() {
        requestSeq.incrementAndGet();
        resolveSeq.incrementAndGet();
        cancelCall(currentSearchCall);
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
