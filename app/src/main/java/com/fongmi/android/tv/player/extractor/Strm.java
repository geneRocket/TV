package com.fongmi.android.tv.player.extractor;

import android.net.Uri;

import com.fongmi.android.tv.Constant;
import com.fongmi.android.tv.player.Source;
import com.fongmi.android.tv.utils.UrlUtil;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Path;
import com.google.common.net.HttpHeaders;

import java.util.Locale;

import okhttp3.Response;

public class Strm implements Source.Extractor {

    @Override
    public boolean match(Uri uri) {
        return UrlUtil.path(uri).toLowerCase(Locale.US).endsWith(".strm");
    }

    @Override
    public String fetch(String url) throws Exception {
        if (url.startsWith("http")) return http(url);
        return firstLine(Path.read(url), url);
    }

    private String http(String url) throws Exception {
        try (Response res = OkHttp.newCall(OkHttp.noRedirect(Constant.TIMEOUT_PARSE_LIVE), url).execute()) {
            return isTextResponse(res, url) && res.body() != null ? firstLine(res.body().string(), url) : url;
        }
    }

    private boolean isTextResponse(Response res, String url) {
        String path = UrlUtil.path(Uri.parse(url)).toLowerCase(Locale.US);
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
