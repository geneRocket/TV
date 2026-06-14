package com.fongmi.android.tv.utils;

import android.net.Uri;
import android.text.TextUtils;

import com.fongmi.android.tv.server.Server;
import com.github.catvod.utils.Json;
import com.github.catvod.utils.UriUtil;
import com.google.common.net.HttpHeaders;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

public class UrlUtil {

    private static final String TAG_HEADERS = "@Headers=";
    private static final String TAG_COOKIE = "@Cookie=";
    private static final String TAG_REFERER = "@Referer=";
    private static final String TAG_USER_AGENT = "@User-Agent=";

    private static final Pattern PATTERN_RELATIVE = Pattern.compile(".*\\.[A-Za-z0-9]{2,5}([?#].*)?$");

    public static Uri uri(String url) {
        return Uri.parse(TextUtils.isEmpty(url) ? "" : url.trim().replace("\\", ""));
    }

    public static String scheme(String url) {
        return scheme(uri(url));
    }

    public static String scheme(Uri uri) {
        String scheme = uri.getScheme();
        return scheme == null ? "" : scheme.toLowerCase().trim();
    }

    public static String host(String url) {
        return host(uri(url));
    }

    public static String host(Uri uri) {
        String host = uri.getHost();
        return host == null ? "" : host.toLowerCase().trim();
    }

    public static String path(Uri uri) {
        String path = uri.getPath();
        return path == null ? "" : path.trim();
    }

    public static String resolve(String baseUri, String referenceUri) {
        return UriUtil.resolve(baseUri, referenceUri);
    }

    public static String convert(String url) {
        return convert(url, uri(url));
    }

    public static String convert(String url, Uri uri) {
        String scheme = scheme(uri);
        if ("clan".equals(scheme)) return convert(fixUrl(url));
        if ("local".equals(scheme)) return url.replace("local://", Server.get().getAddress("/"));
        if ("assets".equals(scheme)) return url.replace("assets://", Server.get().getAddress("/"));
        if ("file".equals(scheme)) return url.replace("file://", Server.get().getAddress("/file/"));
        if ("proxy".equals(scheme)) return url.replace("proxy://", Server.get().getAddress("/proxy?"));
        return url;
    }

    public static String normalize(String url, String baseUri) {
        url = TextUtils.isEmpty(url) ? "" : url.trim().replace("&amp;", "&");
        if ((url.startsWith("\"") && url.endsWith("\"")) || (url.startsWith("'") && url.endsWith("'"))) url = url.substring(1, url.length() - 1).trim();
        int index = findFirstTag(url);
        String suffix = index < 0 ? "" : url.substring(index);
        String link = index < 0 ? url : url.substring(0, index);
        if (link.startsWith("//")) link = "https:" + link;
        Uri uri = uri(link);
        if (!TextUtils.isEmpty(baseUri) && scheme(uri).isEmpty() && !link.startsWith("data:") && isRelativePath(link)) link = resolve(baseUri, link);
        return convert(link + suffix, uri);
    }

    private static boolean isRelativePath(String url) {
        if (TextUtils.isEmpty(url)) return false;
        if (url.startsWith("/") || url.startsWith("./") || url.startsWith("../") || url.startsWith("?")) return true;
        return url.contains("/") || PATTERN_RELATIVE.matcher(url).matches();
    }

    public static String stripTag(String url) {
        int index = findFirstTag(url);
        return index < 0 ? url : url.substring(0, index);
    }

    public static Map<String, String> getTagHeaders(String url) {
        Map<String, String> headers = new LinkedHashMap<>();
        try {
            String value = getParam(url, TAG_HEADERS);
            if (!TextUtils.isEmpty(value)) headers.putAll(fixHeaders(Json.toMap(Json.parse(value))));
            value = getParam(url, TAG_COOKIE);
            if (!TextUtils.isEmpty(value)) headers.put(HttpHeaders.COOKIE, value);
            value = getParam(url, TAG_REFERER);
            if (!TextUtils.isEmpty(value)) headers.put(HttpHeaders.REFERER, value);
            value = getParam(url, TAG_USER_AGENT);
            if (!TextUtils.isEmpty(value)) headers.put(HttpHeaders.USER_AGENT, value);
        } catch (Exception ignored) {
        }
        return headers;
    }

    private static Map<String, String> fixHeaders(Map<String, String> headers) {
        Map<String, String> result = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : headers.entrySet()) result.put(fixHeader(entry.getKey()), entry.getValue());
        return result;
    }

    private static String getParam(String url, String tag) {
        int start = url.indexOf(tag);
        if (start < 0) return "";
        start += tag.length();
        int end = findNextTag(url, start);
        return (end < 0 ? url.substring(start) : url.substring(start, end)).trim();
    }

    private static int findFirstTag(String url) {
        int index = -1;
        for (String tag : new String[]{TAG_HEADERS, TAG_COOKIE, TAG_REFERER, TAG_USER_AGENT}) {
            int found = url.indexOf(tag);
            if (found >= 0 && (index < 0 || found < index)) index = found;
        }
        return index;
    }

    private static int findNextTag(String url, int start) {
        int index = -1;
        for (String tag : new String[]{TAG_HEADERS, TAG_COOKIE, TAG_REFERER, TAG_USER_AGENT}) {
            int found = url.indexOf(tag, start);
            if (found >= 0 && (index < 0 || found < index)) index = found;
        }
        return index;
    }

    public static String fixUrl(String url) {
        if (url.contains("/localhost/")) url = url.replace("/localhost/", "/");
        if (url.startsWith("clan")) url = url.replace("clan", "file");
        return url;
    }

    public static String fixHeader(String key) {
        if (HttpHeaders.USER_AGENT.equalsIgnoreCase(key)) return HttpHeaders.USER_AGENT;
        if (HttpHeaders.REFERER.equalsIgnoreCase(key)) return HttpHeaders.REFERER;
        if (HttpHeaders.COOKIE.equalsIgnoreCase(key)) return HttpHeaders.COOKIE;
        return key;
    }

    public static String fixDownloadUrl(String url) {
        if (TextUtils.isEmpty(url)) return "";
        Uri uri = UrlUtil.uri(url);
        if (!uri.toString().startsWith("http://127.0.0.1:")) return uri.toString();
        String download = uri.getQueryParameter("url");
        return TextUtils.isEmpty(download) ? uri.toString() : download;
    }
}
