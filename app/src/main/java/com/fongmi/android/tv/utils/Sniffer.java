package com.fongmi.android.tv.utils;

import android.net.Uri;
import android.text.TextUtils;

import com.fongmi.android.tv.api.config.LiveConfig;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.Rule;
import com.github.catvod.utils.Json;
import com.github.catvod.utils.Util;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Sniffer {

    public static final Pattern CLICKER = Pattern.compile("\\[a=cr:(\\{.*?\\})\\/](.*?)\\[\\/a]");
    public static final Pattern AI_PUSH = Pattern.compile("(http|https|rtmp|rtsp|smb|ftp|thunder|magnet|ed2k|mitv|tvbox-xg|jianpian|video):[^\\s]+", Pattern.MULTILINE);
    public static final Pattern SNIFFER = Pattern.compile("http((?!http).){12,}?\\.(m3u8|mp4|mkv|flv|mp3|m4a|aac|mpd)\\?.*|http((?!http).){12,}\\.(m3u8|mp4|mkv|flv|mp3|m4a|aac|mpd)|http((?!http).)*?video/tos*|http((?!http).)*?obj/tos*");

    public static String getUrl(String text) {
        if (Json.valid(text) || text.contains("$")) return text;
        Matcher m = AI_PUSH.matcher(text);
        if (m.find()) return m.group(0);
        return text;
    }

    public static boolean isVideoFormat(String url) {
        if (url.endsWith(".m3u8") || url.endsWith(".mp4") || url.endsWith(".mkv") || url.endsWith(".flv")) return true;
        if (url.contains(".m3u8?") || url.contains(".mp4?") || url.contains(".mkv?") || url.contains(".flv?")) return true;
        Rule rule = getRule(UrlUtil.uri(url));
        for (String exclude : rule.getExclude()) if (url.contains(exclude)) return false;
        for (Pattern pattern : rule.getExcludePatterns()) if (pattern.matcher(url).find()) return false;
        for (String regex : rule.getRegex()) if (url.contains(regex)) return true;
        for (Pattern pattern : rule.getRegexPatterns()) if (pattern.matcher(url).find()) return true;
        if (url.contains("url=http") || url.contains("v=http") || url.contains(".html")) return false;
        return SNIFFER.matcher(url).find();
    }

    public static Rule getRule(Uri uri) {
        if (uri.getHost() == null) return Rule.empty();
        String hosts = TextUtils.join(",", Arrays.asList(UrlUtil.host(uri), UrlUtil.host(uri.getQueryParameter("url"))));
        for (Rule rule : VodConfig.get().getRules()) for (String host : rule.getHosts()) if (Util.containOrMatch(hosts, host)) return rule;
        for (Rule rule : LiveConfig.get().getRules()) for (String host : rule.getHosts()) if (Util.containOrMatch(hosts, host)) return rule;
        return Rule.empty();
    }

    public static List<String> getRegex(Uri uri) {
        return getRule(uri).getRegex();
    }

    public static List<String> getScript(Uri uri) {
        return getRule(uri).getScript();
    }
}
