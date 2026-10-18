package com.fongmi.android.tv.utils;

import android.net.Uri;
import android.text.TextUtils;

import com.fongmi.android.tv.Setting;
import com.fongmi.android.tv.api.config.LiveConfig;
import com.fongmi.android.tv.api.config.VodConfig;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class AdBlocker {

    private static final Set<String> DEFAULT_HOSTS = new HashSet<>(Arrays.asList(
            "doubleclick.net",
            "googlesyndication.com",
            "googleadservices.com",
            "google-analytics.com",
            "g.doubleclick.net",
            "pagead2.googlesyndication.com",
            "securepubads.g.doubleclick.net",
            "imasdk.googleapis.com",
            "adsystem.com",
            "adsrvr.org",
            "adnxs.com",
            "rubiconproject.com",
            "pubmatic.com",
            "criteo.com",
            "scorecardresearch.com",
            "moatads.com",
            "serving-sys.com",
            "spotx.tv",
            "freewheel.tv",
            "innovid.com",
            "v.fwmrm.net",
            "adform.net",
            "taboola.com",
            "outbrain.com",
            "czzycdn.com",
            "changzeyun.com"
    ));

    private static final Set<String> DEFAULT_TOKENS = new HashSet<>(Arrays.asList(
            "ad", "ads", "adv", "advert", "advertising", "commercial", "doubleclick",
            "gampad", "ima", "vast", "vmap", "preroll", "midroll", "postroll",
            "adbreak", "adpod", "scte35", "ssai", "csai", "tracking", "beacon",
            "changze", "czzy", "czad", "changzeyun", "changzepiepian",
            "guanggao", "imads", "adjump", "adslice", "slicead", "popad", "popads",
            "splashad", "casino", "gambling", "kudad", "kudianad", "chabo", "chaboad",
            "kaipian", "kaipianad", "piantou", "piantouad", "pianwei", "pianweiad",
            "paomadeng", "macau", "pujing", "xinpujing", "bet365"
    ));

    private static volatile AdsRules rules = AdsRules.empty();

    private static final Pattern PATTERN_TOKEN_SPLIT = Pattern.compile("[^a-z0-9]+");

    private AdBlocker() {
    }

    public static boolean isAdUrl(String url) {
        if (TextUtils.isEmpty(url)) return false;
        AdsRules rules = ensureRules();
        String value = url.toLowerCase(Locale.US);
        Uri uri = UrlUtil.uri(value);
        String host = uri.getHost();
        if (matchesConfiguredHost(rules, host) || matchesConfiguredUrl(rules, value)) return true;
        if (!Setting.isRemoveAd()) return false;
        return hasDefaultAdHost(host) || hasDefaultAdSignal(value);
    }

    public static boolean isAdHost(String host) {
        if (TextUtils.isEmpty(host)) return false;
        AdsRules rules = ensureRules();
        String value = host.toLowerCase(Locale.US);
        if (matchesConfiguredHost(rules, value)) return true;
        return Setting.isRemoveAd() && hasDefaultAdHost(value);
    }

    private static boolean matchesConfiguredHost(AdsRules rules, String host) {
        if (TextUtils.isEmpty(host)) return false;
        if (rules.hosts.contains(host)) return true;
        int index = host.lastIndexOf('.');
        if (index > 0) {
            int prev = host.lastIndexOf('.', index - 1);
            if (prev != -1) {
                String domain = host.substring(prev + 1);
                if (rules.hosts.contains(domain)) return true;
            }
        }
        for (Pattern pattern : rules.hostPatterns) if (pattern.matcher(host).find()) return true;
        return false;
    }

    private static boolean matchesConfiguredUrl(AdsRules rules, String value) {
        for (String ad : rules.urls) if (value.contains(ad)) return true;
        for (Pattern pattern : rules.urlPatterns) if (pattern.matcher(value).find()) return true;
        return false;
    }

    private static AdsRules ensureRules() {
        List<String> vodAds = VodConfig.get().getAds();
        List<String> liveAds = LiveConfig.get().getAds();
        AdsRules current = rules;
        if (current.matches(vodAds, liveAds)) return current;
        synchronized (AdBlocker.class) {
            current = rules;
            if (!current.matches(vodAds, liveAds)) rules = current = AdsRules.create(vodAds, liveAds);
            return current;
        }
    }

    private static void parseRules(List<String> ads, Set<String> hosts, List<String> urls) {
        for (String ad : ads) {
            if (TextUtils.isEmpty(ad) || isRegexRule(ad)) continue;
            if (isHostRule(ad)) hosts.add(normalizeRule(ad));
            else urls.add(normalizeRule(ad));
        }
    }

    private static String normalizeRule(String rule) {
        if (rule == null) return "";
        String value = rule.trim();
        if (value.regionMatches(true, 0, "regex:", 0, 6)) return value.substring(6).trim();
        return value.toLowerCase(Locale.US);
    }

    private static boolean isRegexRule(String rule) {
        if (rule == null || rule.isEmpty()) return false;
        return rule.trim().toLowerCase(Locale.US).startsWith("regex:");
    }

    private static List<Pattern> compilePatterns(List<String> ads, boolean hostOnly) {
        if (ads.isEmpty()) return Collections.emptyList();
        List<Pattern> patterns = new ArrayList<>(ads.size());
        for (String ad : ads) {
            if (ad == null || ad.trim().isEmpty()) continue;
            if (hostOnly != isHostRule(ad)) continue;
            // Legacy configurations use bare regexes as well as literal substrings.
            // Preserve regex escapes such as \\D when accepting either syntax.
            String expression = isRegexRule(ad) ? normalizeRule(ad) : ad.trim();
            try {
                patterns.add(Pattern.compile(expression, Pattern.CASE_INSENSITIVE));
            } catch (Exception e) {
                patterns.add(Pattern.compile(Pattern.quote(expression), Pattern.CASE_INSENSITIVE));
            }
        }
        return patterns;
    }

    private static final class AdsRules {

        private final List<String> vodAds;
        private final List<String> liveAds;
        private final Set<String> hosts;
        private final List<String> urls;
        private final List<Pattern> hostPatterns;
        private final List<Pattern> urlPatterns;

        private AdsRules(List<String> vodAds, List<String> liveAds, Set<String> hosts, List<String> urls, List<Pattern> hostPatterns, List<Pattern> urlPatterns) {
            this.vodAds = Collections.unmodifiableList(new ArrayList<>(vodAds));
            this.liveAds = Collections.unmodifiableList(new ArrayList<>(liveAds));
            this.hosts = hosts;
            this.urls = urls;
            this.hostPatterns = hostPatterns;
            this.urlPatterns = urlPatterns;
        }

        private boolean matches(List<String> vodAds, List<String> liveAds) {
            return this.vodAds.equals(vodAds) && this.liveAds.equals(liveAds);
        }

        private static AdsRules empty() {
            return new AdsRules(Collections.emptyList(), Collections.emptyList(), Collections.emptySet(), Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
        }

        private static AdsRules create(List<String> vodAds, List<String> liveAds) {
            Set<String> hosts = new HashSet<>();
            List<String> urls = new ArrayList<>();
            parseRules(vodAds, hosts, urls);
            parseRules(liveAds, hosts, urls);
            List<Pattern> hostPatterns = new ArrayList<>(compilePatterns(vodAds, true));
            hostPatterns.addAll(compilePatterns(liveAds, true));
            List<Pattern> urlPatterns = new ArrayList<>(compilePatterns(vodAds, false));
            urlPatterns.addAll(compilePatterns(liveAds, false));
            return new AdsRules(vodAds, liveAds, hosts, urls, hostPatterns, urlPatterns);
        }
    }

    private static boolean isHostRule(String rule) {
        return rule != null && !rule.isEmpty() && !isUrlRule(rule);
    }

    private static boolean isUrlRule(String rule) {
        if (rule == null || rule.isEmpty()) return false;
        String value = rule.trim();
        return value.contains("://")
                || value.startsWith("/")
                || value.contains("/")
                || value.contains("?") && value.contains("=")
                || value.contains("&") && value.contains("=");
    }

    private static boolean hasDefaultAdHost(String host) {
        if (TextUtils.isEmpty(host)) return false;
        String value = host.toLowerCase(Locale.US);
        for (String ad : DEFAULT_HOSTS) if (value.equals(ad) || value.endsWith("." + ad)) return true;
        return false;
    }

    static boolean hasDefaultAdSignal(String url) {
        if (url == null || url.isEmpty()) return false;
        try {
            String path = Objects.toString(new java.net.URI(url).getPath(), "").toLowerCase(Locale.US);
            return hasToken(path) || M3u8AdFilter.containsAdQueryKey(url);
        } catch (java.net.URISyntaxException error) {
            return false;
        }
    }

    private static boolean hasToken(String value) {
        if (value == null || value.isEmpty()) return false;
        Matcher matcher = PATTERN_TOKEN_SPLIT.matcher(value);
        int start = 0;
        while (matcher.find()) {
            String token = value.substring(start, matcher.start());
            if (isAdToken(token)) return true;
            start = matcher.end();
        }
        return isAdToken(value.substring(start));
    }

    private static boolean isAdToken(String token) {
        if (token.isEmpty()) return false;
        if (DEFAULT_TOKENS.contains(token)) return true;
        if (token.startsWith("advert")) return true;
        return token.startsWith("adbreak") || token.startsWith("adpod");
    }
}
