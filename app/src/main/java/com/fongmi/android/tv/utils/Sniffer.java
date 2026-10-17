package com.fongmi.android.tv.utils;

import android.net.Uri;

import com.fongmi.android.tv.api.config.LiveConfig;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.Rule;
import com.github.catvod.utils.Json;
import com.github.catvod.utils.Util;

import java.util.List;
import java.util.Locale;
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
        if (url == null || url.isEmpty()) return false;
        return isVideoFormat(url, getRule(UrlUtil.uri(url)));
    }

    static boolean isVideoFormat(String url, Rule rule) {
        String lowerUrl = url.toLowerCase(Locale.ROOT);
        for (String exclude : rule.getExclude()) if (lowerUrl.contains(exclude.toLowerCase(Locale.ROOT))) return false;
        for (Pattern pattern : rule.getExcludePatterns()) if (pattern.matcher(url).find()) return false;
        for (String regex : rule.getRegex()) if (lowerUrl.contains(regex.toLowerCase(Locale.ROOT))) return true;
        for (Pattern pattern : rule.getRegexPatterns()) if (pattern.matcher(url).find()) return true;
        if (lowerUrl.contains("url=http") || lowerUrl.contains("v=http") || lowerUrl.contains(".html")) return false;
        if (lowerUrl.endsWith(".m3u8") || lowerUrl.endsWith(".mp4") || lowerUrl.endsWith(".mkv") || lowerUrl.endsWith(".flv")) return true;
        if (lowerUrl.contains(".m3u8?") || lowerUrl.contains(".mp4?") || lowerUrl.contains(".mkv?") || lowerUrl.contains(".flv?")) return true;
        return SNIFFER.matcher(url).find();
    }

    public static synchronized Rule getRule(Uri uri) {
        if (uri.getHost() == null) return Rule.empty();
        String host = UrlUtil.host(uri);
        String query = UrlUtil.host(uri.getQueryParameter("url"));
        Rule rule = findRule(VodConfig.get().getRules(), host, query);
        if (rule == null) rule = findRule(LiveConfig.get().getRules(), host, query);
        return rule == null ? Rule.empty() : rule;
    }

    static Rule findRule(List<Rule> rules, String host, String query) {
        for (Rule rule : rules) {
            for (String pattern : rule.getHosts()) {
                if (Util.containOrMatch(host, pattern) || Util.containOrMatch(query, pattern)) return rule;
            }
        }
        return null;
    }

    public static List<String> getRegex(Uri uri) {
        return getRule(uri).getRegex();
    }

    public static List<String> getScript(Uri uri) {
        return getRule(uri).getScript();
    }
}
