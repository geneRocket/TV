package com.fongmi.android.tv.player;

import com.fongmi.android.tv.Setting;
import com.google.common.net.HttpHeaders;

import java.util.HashMap;
import java.util.Map;

public final class PlaybackHeaders {

    private PlaybackHeaders() {}

    public static Map<String, String> applyUserAgent(Map<String, String> headers) {
        Map<String, String> result = headers == null ? new HashMap<>() : new HashMap<>(headers);
        if (Setting.getUa().isEmpty()) return result;
        for (String name : result.keySet()) if (HttpHeaders.USER_AGENT.equalsIgnoreCase(name)) return result;
        result.put(HttpHeaders.USER_AGENT, Setting.getUa());
        return result;
    }
}
