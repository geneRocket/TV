package com.fongmi.android.tv.utils;

import android.net.Uri;
import android.text.TextUtils;

import com.fongmi.android.tv.api.config.LiveConfig;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.Rule;
import com.github.catvod.utils.Json;
import com.github.catvod.utils.Util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Sniffer {

    public static final Pattern CLICKER = Pattern.compile("\\[a=cr:(\\{.*?\\})\\/](.*?)\\[\\/a]");
    public static final Pattern AI_PUSH = Pattern.compile("(http|https|rtmp|rtsp|smb|ftp|thunder|magnet|ed2k|mitv|tvbox-xg|jianpian|video):[^\\s]+", Pattern.MULTILINE);
    public static final Pattern SNIFFER = Pattern.compile("http((?!http).){12,}?\\.(m3u8|mp4|mkv|flv|mp3|m4a|aac|mpd)\\?.*|http((?!http).){12,}\\.(m3u8|mp4|mkv|flv|mp3|m4a|aac|mpd)|http((?!http).)*?video/tos*|http((?!http).)*?obj/tos*");

    private static final Map<String, Rule> RULE_CACHE = new HashMap<>();
    private static final List<Rule> REGEX_RULES = new ArrayList<>();
    private static int rulesHash;

    public static String getUrl(String text) {
        if (Json.valid(text) || text.contains("$")) return text;
        Matcher m = AI_PUSH.matcher(text);
        if (m.find()) return m.group(0);
        return text;
    }

    public static boolean isVideoFormat(String url) {
        if (TextUtils.isEmpty(url)) return false;
        String lowerUrl = url.toLowerCase();
        if (lowerUrl.endsWith(".m3u8") || lowerUrl.endsWith(".mp4") || lowerUrl.endsWith(".mkv") || lowerUrl.endsWith(".flv")) return true;
        if (lowerUrl.contains(".m3u8?") || lowerUrl.contains(".mp4?") || lowerUrl.contains(".mkv?") || lowerUrl.contains(".flv?")) return true;
        Rule rule = getRule(UrlUtil.uri(url));
        for (String exclude : rule.getExclude()) if (lowerUrl.contains(exclude.toLowerCase())) return false;
        for (Pattern pattern : rule.getExcludePatterns()) if (pattern.matcher(url).find()) return false;
        for (String regex : rule.getRegex()) if (lowerUrl.contains(regex.toLowerCase())) return true;
        for (Pattern pattern : rule.getRegexPatterns()) if (pattern.matcher(url).find()) return true;
        if (lowerUrl.contains("url=http") || lowerUrl.contains("v=http") || lowerUrl.contains(".html")) return false;
        return SNIFFER.matcher(url).find();
    }

    public static Rule getRule(Uri uri) {
        if (uri.getHost() == null) return Rule.empty();
        ensureRules();
        String host = UrlUtil.host(uri);
        String query = UrlUtil.host(uri.getQueryParameter("url"));
        Rule rule = RULE_CACHE.get(host);
        if (rule != null) return rule;
        rule = RULE_CACHE.get(query);
        if (rule != null) return rule;
        for (Rule item : REGEX_RULES) {
            for (String h : item.getHosts()) {
                if (Util.containOrMatch(host, h) || Util.containOrMatch(query, h)) return item;
            }
        }
        return Rule.empty();
    }

    private static void ensureRules() {
        List<Rule> vodRules = VodConfig.get().getRules();
        List<Rule> liveRules = LiveConfig.get().getRules();
        int hash = vodRules.hashCode() + liveRules.hashCode();
        if (rulesHash == hash) return;
        rulesHash = hash;
        RULE_CACHE.clear();
        REGEX_RULES.clear();
        parseRules(vodRules);
        parseRules(liveRules);
    }

    private static void parseRules(List<Rule> rules) {
        for (Rule rule : rules) {
            boolean regex = false;
            for (String host : rule.getHosts()) {
                if (host.contains("*")) regex = true;
                else RULE_CACHE.put(host, rule);
            }
            if (regex) REGEX_RULES.add(rule);
        }
    }

    public static List<String> getRegex(Uri uri) {
        return getRule(uri).getRegex();
    }

    public static List<String> getScript(Uri uri) {
        return getRule(uri).getScript();
    }
}
