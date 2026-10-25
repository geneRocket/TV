package com.fongmi.android.tv.subtitle;

import com.fongmi.android.tv.bean.Subtitle;
import com.fongmi.android.tv.utils.TaskScheduler;
import java.io.IOException;
import java.util.function.Function;
import okhttp3.*;

/** HTTP adapter. Owns response resources; callers own cancellation and publication. */
public final class HttpSubtitleGateway implements SubtitleGateway {
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/94.0.4606.54 Safari/537.36";
    private final Call.Factory calls;
    private final TaskScheduler deadlines;
    private final long timeout;
    public HttpSubtitleGateway(Call.Factory calls) { this(calls,null,0); }
    public HttpSubtitleGateway(Call.Factory calls, TaskScheduler deadlines, long timeout) {
        this.calls = calls; this.deadlines = deadlines; this.timeout = timeout;
    }

    @Override public Request search(SubtitleSource source, String keyword, int page, Callback<Page> callback) {
        switch (source) {
            case ALL: return SubtitleFanOut.start(page, (site, result) -> search(site, keyword, page, result), callback);
            case SUBHD: {
                HttpUrl.Builder url = HttpUrl.parse(SubhdSubtitleParser.BASE).newBuilder().addPathSegment("search").addPathSegment(keyword);
                if (page > 1) url.addPathSegment(String.valueOf(page));
                return text(url.build().toString(), SubhdSubtitleParser.BASE,
                        html -> SubhdSubtitleParser.search(html, keyword, page), callback);
            }
            case WEBSITE:
                if (page <= 1) return fallback(keyword, callback);
                callback.success(new Page(java.util.Collections.emptyList(),false,false,-1));
                return () -> {};
            default: return search(keyword, page, callback);
        }
    }

    @Override public Request search(String keyword, int page, Callback<Page> callback) {
        HttpUrl url = HttpUrl.parse(AssrtSubtitleParser.BASE + "/sub/").newBuilder()
                .addQueryParameter("searchword", keyword).addQueryParameter("sort", "rank")
                .addQueryParameter("page", String.valueOf(page)).addQueryParameter("no_redir", "1").build();
        return text(url.toString(), AssrtSubtitleParser.BASE,
                html -> AssrtSubtitleParser.search(html, keyword, page), callback);
    }

    @Override public Request fallback(String keyword, Callback<Page> callback) {
        HttpUrl url = HttpUrl.parse(WebsiteSubtitleParser.BASE + "/api/search/subtitles").newBuilder()
                .addQueryParameter("title", keyword).addQueryParameter("format", "srt").build();
        return text(url.toString(), WebsiteSubtitleParser.BASE,
                json -> WebsiteSubtitleParser.search(json, keyword), callback);
    }

    @Override public Request details(Subtitle subtitle, Callback<Page> callback) {
        return execute(subtitle.getUrl(), AssrtSubtitleParser.DOWNLOAD, response ->
                AssrtSubtitleParser.details(body(response), response.request().url().toString()), callback);
    }

    @Override public Request resolve(Subtitle subtitle, Callback<String> callback) {
        if (subtitle.isExternal()) throw new IllegalArgumentException("Website result is not a playable subtitle");
        if ("Subtitles.website".equals(subtitle.getSource())) {
            callback.success(subtitle.getUrl()); return () -> {};
        }
        return execute(subtitle.getUrl(), AssrtSubtitleParser.BASE, response ->
                AssrtSubtitleParser.normalizeAssrtLocation(response.request().url().toString(), response.header("location")), callback);
    }

    private <T> Request text(String url, String referer, Function<String, T> parser, Callback<T> callback) {
        return execute(url, referer, response -> parser.apply(body(response)), callback);
    }

    private interface Read<T> { T read(Response response) throws Exception; }
    private <T> Request execute(String url, String referer, Read<T> read, Callback<T> callback) {
        okhttp3.Request request = new okhttp3.Request.Builder().url(url).get()
                .header("Referer", referer).header("User-Agent", USER_AGENT).build();
        Call call = calls.newCall(request);
        java.util.concurrent.atomic.AtomicBoolean finished = new java.util.concurrent.atomic.AtomicBoolean();
        Runnable deadline = () -> {
            if (finished.compareAndSet(false,true)) {
                call.cancel();
                callback.failure(new java.net.SocketTimeoutException("Subtitle source timed out"));
            }
        };
        if (deadlines != null && timeout > 0) deadlines.post(deadline,timeout);
        try { call.enqueue(new okhttp3.Callback() {
            @Override public void onFailure(Call call, IOException error) {
                if (finished.compareAndSet(false,true)) { unschedule(deadline); callback.failure(error); }
            }
            @Override public void onResponse(Call call, Response response) {
                T value;
                try (Response owned = response) {
                    if (finished.get()) return;
                    value = read.read(owned);
                } catch (Exception error) {
                    if (finished.compareAndSet(false,true)) { unschedule(deadline); callback.failure(error); }
                    return;
                }
                if (finished.compareAndSet(false,true)) { unschedule(deadline); callback.success(value); }
            }
        }); } catch (RuntimeException error) {
            finished.set(true); unschedule(deadline); call.cancel(); throw error;
        }
        return () -> { finished.set(true); unschedule(deadline); call.cancel(); };
    }

    private void unschedule(Runnable task) { if (deadlines != null) deadlines.remove(task); }

    private static String body(Response response) throws IOException {
        if (!response.isSuccessful() || response.body() == null) throw new IOException("Subtitle HTTP " + response.code());
        return response.body().string();
    }
}
