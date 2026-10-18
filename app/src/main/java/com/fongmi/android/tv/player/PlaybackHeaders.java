package com.fongmi.android.tv.player;

import com.fongmi.android.tv.Setting;
import com.fongmi.android.tv.utils.UrlUtil;
import com.google.common.net.HttpHeaders;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

public final class PlaybackHeaders {

    private PlaybackHeaders() {}

    static Map<String, String> mergeInlineHeaders(Map<String, String> headers, String url) {
        Map<String, String> result = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        if (headers != null) result.putAll(headers);
        result.putAll(UrlUtil.getTagHeaders(url));
        return result;
    }

    public static Map<String, String> applyUserAgent(Map<String, String> headers) {
        Map<String, String> result = headers == null ? new HashMap<>() : new HashMap<>(headers);
        if (Setting.getUa().isEmpty()) return result;
        for (String name : result.keySet()) if (HttpHeaders.USER_AGENT.equalsIgnoreCase(name)) return result;
        result.put(HttpHeaders.USER_AGENT, Setting.getUa());
        return result;
    }
}
