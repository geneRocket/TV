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
            "outbrain.com"
    ));

    private static final Set<String> DEFAULT_TOKENS = new HashSet<>(Arrays.asList(
            "ad", "ads", "adv", "advert", "advertising", "commercial", "doubleclick",
            "gampad", "ima", "vast", "vmap", "preroll", "midroll", "postroll",
            "adbreak", "adpod", "scte35", "ssai", "csai", "tracking", "beacon"
    ));

    private static int vodAdsHash;
    private static int liveAdsHash;
    private static final Set<String> vodHosts = new HashSet<>();
    private static final Set<String> liveHosts = new HashSet<>();
    private static final List<String> vodUrls = new ArrayList<>();
    private static final List<String> liveUrls = new ArrayList<>();
    private static List<Pattern> vodHostPatterns = Collections.emptyList();
    private static List<Pattern> liveHostPatterns = Collections.emptyList();
    private static List<Pattern> vodUrlPatterns = Collections.emptyList();
    private static List<Pattern> liveUrlPatterns = Collections.emptyList();

    private AdBlocker() {
    }

    public static boolean isAdUrl(String url) {
        if (TextUtils.isEmpty(url)) return false;
        ensurePatterns();
        String value = url.toLowerCase(Locale.US);
        Uri uri = UrlUtil.uri(value);
        if (matchesConfiguredHost(uri.getHost()) || matchesConfiguredUrl(value)) return true;
        if (!Setting.isRemoveAd()) return false;
        return hasDefaultAdHost(uri.getHost()) || hasDefaultAdSignal(uri);
    }

    public static boolean isAdHost(String host) {
        if (TextUtils.isEmpty(host)) return false;
        ensurePatterns();
        String value = host.toLowerCase(Locale.US);
        if (matchesConfiguredHost(value)) return true;
        return Setting.isRemoveAd() && hasDefaultAdHost(value);
    }

    private static boolean matchesConfiguredHost(String host) {
        if (TextUtils.isEmpty(host)) return false;
        if (vodHosts.contains(host) || liveHosts.contains(host)) return true;
        for (String ad : vodHosts) if (host.endsWith("." + ad)) return true;
        for (String ad : liveHosts) if (host.endsWith("." + ad)) return true;
        for (Pattern pattern : vodHostPatterns) if (pattern.matcher(host).find()) return true;
        for (Pattern pattern : liveHostPatterns) if (pattern.matcher(host).find()) return true;
        return false;
    }

    private static boolean matchesConfiguredUrl(String value) {
        for (String ad : vodUrls) if (value.contains(ad)) return true;
        for (String ad : liveUrls) if (value.contains(ad)) return true;
        for (Pattern pattern : vodUrlPatterns) if (pattern.matcher(value).find()) return true;
        for (Pattern pattern : liveUrlPatterns) if (pattern.matcher(value).find()) return true;
        return false;
    }

    private static synchronized void ensurePatterns() {
        List<String> vodAds = VodConfig.get().getAds();
        List<String> liveAds = LiveConfig.get().getAds();
        int vodHash = vodAds.hashCode();
        int liveHash = liveAds.hashCode();
        if (vodHash != vodAdsHash) {
            vodAdsHash = vodHash;
            vodHosts.clear();
            vodUrls.clear();
            parseRules(vodAds, vodHosts, vodUrls);
            vodHostPatterns = compilePatterns(vodAds, true);
            vodUrlPatterns = compilePatterns(vodAds, false);
        }
        if (liveHash != liveAdsHash) {
            liveAdsHash = liveHash;
            liveHosts.clear();
            liveUrls.clear();
            parseRules(liveAds, liveHosts, liveUrls);
            liveHostPatterns = compilePatterns(liveAds, true);
            liveUrlPatterns = compilePatterns(liveAds, false);
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
        String value = rule.trim().toLowerCase(Locale.US);
        if (value.startsWith("regex:")) value = value.substring(6).trim();
        return value;
    }

    private static boolean isRegexRule(String rule) {
        if (TextUtils.isEmpty(rule)) return false;
        return rule.trim().toLowerCase(Locale.US).startsWith("regex:");
    }

    private static List<Pattern> compilePatterns(List<String> ads, boolean hostOnly) {
        if (ads.isEmpty()) return Collections.emptyList();
        List<Pattern> patterns = new ArrayList<>(ads.size());
        for (String ad : ads) {
            if (TextUtils.isEmpty(ad)) continue;
            if (hostOnly != isHostRule(ad)) continue;
            if (!isRegexRule(ad)) continue;
            try {
                patterns.add(Pattern.compile(normalizeRule(ad), Pattern.CASE_INSENSITIVE));
            } catch (Exception e) {
                patterns.add(Pattern.compile(Pattern.quote(normalizeRule(ad)), Pattern.CASE_INSENSITIVE));
            }
        }
        return patterns;
    }

    private static boolean isHostRule(String rule) {
        return !TextUtils.isEmpty(rule) && !isUrlRule(rule);
    }

    private static boolean isUrlRule(String rule) {
        if (TextUtils.isEmpty(rule)) return false;
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

    private static boolean hasDefaultAdSignal(Uri uri) {
        String path = Objects.toString(uri.getPath(), "").toLowerCase(Locale.US);
        if (hasToken(path)) return true;
        String query = Objects.toString(uri.getEncodedQuery(), "").toLowerCase(Locale.US);
        return hasToken(query);
    }

    private static boolean hasToken(String value) {
        if (TextUtils.isEmpty(value)) return false;
        for (String token : value.split("[^a-z0-9]+")) {
            if (TextUtils.isEmpty(token)) continue;
            if (DEFAULT_TOKENS.contains(token)) return true;
            if (token.startsWith("advert")) return true;
            if (token.startsWith("adbreak") || token.startsWith("adpod")) return true;
        }
        return false;
    }
}
