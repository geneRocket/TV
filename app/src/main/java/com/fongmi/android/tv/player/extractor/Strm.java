package com.fongmi.android.tv.player.extractor;

import android.net.Uri;

import com.fongmi.android.tv.Constant;
import com.fongmi.android.tv.player.Source;
import com.fongmi.android.tv.utils.UrlUtil;
import com.github.catvod.net.OkHttp;
import com.github.catvod.net.HttpCallScope;
import com.github.catvod.utils.Path;
import com.google.common.net.HttpHeaders;

import java.io.IOException;
import java.util.Locale;

import okhttp3.Headers;
import okhttp3.Request;
import okhttp3.Response;

public class Strm implements Source.Extractor {

    @Override
    public boolean match(Uri uri) {
        return UrlUtil.path(uri).toLowerCase(Locale.US).endsWith(".strm");
    }

    @Override
    public String fetch(String url) throws Exception {
        if (url.startsWith("http")) return http(url);
        return firstLine(Path.read(UrlUtil.stripTag(url)), url);
    }

    private String http(String url) throws Exception {
        Request request = new Request.Builder().url(UrlUtil.stripTag(url))
                .headers(Headers.of(UrlUtil.getTagHeaders(url))).build();
        try (Response res = HttpCallScope.track(OkHttp.noRedirect(Constant.TIMEOUT_PARSE_LIVE).newCall(request)).execute()) {
            if (res.isRedirect()) return url;
            if (!res.isSuccessful()) throw new IOException(res.code() + " " + res.message());
            return isTextResponse(res) && res.body() != null ? firstLine(res.body().string(), url) : url;
        }
    }

    private boolean isTextResponse(Response res) {
        String path = res.request().url().encodedPath().toLowerCase(Locale.US);
        String disposition = res.header(HttpHeaders.CONTENT_DISPOSITION, "").toLowerCase(Locale.US);
        String contentType = res.header(HttpHeaders.CONTENT_TYPE, "").toLowerCase(Locale.US);
        return path.endsWith(".strm") || path.endsWith(".txt") || disposition.contains(".strm") || disposition.contains(".txt") || contentType.startsWith("text/");
    }

    private String firstLine(String content, String fallback) {
        if (content == null) return fallback;
        String text = content.replace("\uFEFF", "");
        for (String line : text.split("\\R")) if (!line.trim().isEmpty()) return line.trim();
        return fallback;
    }

    @Override
    public void stop() {
    }

    @Override
    public void exit() {
    }
}
