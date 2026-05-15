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
    private static List<Pattern> vodPatterns = Collections.emptyList();
    private static List<Pattern> livePatterns = Collections.emptyList();

    private AdBlocker() {
    }

    public static boolean isAdUrl(String url) {
        if (TextUtils.isEmpty(url)) return false;
        String value = url.toLowerCase(Locale.US);
        if (matchesConfiguredAds(value)) return true;
        if (!Setting.isRemoveAd()) return false;
        Uri uri = UrlUtil.uri(value);
        return hasDefaultAdHost(uri.getHost()) || hasDefaultAdSignal(uri);
    }

    public static boolean isAdHost(String host) {
        if (TextUtils.isEmpty(host)) return false;
        String value = host.toLowerCase(Locale.US);
        if (matchesConfiguredAds(value)) return true;
        return Setting.isRemoveAd() && hasDefaultAdHost(value);
    }

    private static boolean matchesConfiguredAds(String value) {
        List<String> vodAds = VodConfig.get().getAds();
        List<String> liveAds = LiveConfig.get().getAds();
        for (String ad : vodAds) if (contains(value, ad)) return true;
        for (String ad : liveAds) if (contains(value, ad)) return true;
        ensurePatterns(vodAds, liveAds);
        for (Pattern pattern : vodPatterns) if (pattern.matcher(value).find()) return true;
        for (Pattern pattern : livePatterns) if (pattern.matcher(value).find()) return true;
        return false;
    }

    private static boolean contains(String value, String ad) {
        return !TextUtils.isEmpty(ad) && value.contains(ad.toLowerCase(Locale.US));
    }

    private static void ensurePatterns(List<String> vodAds, List<String> liveAds) {
        int vodHash = vodAds.hashCode();
        int liveHash = liveAds.hashCode();
        if (vodHash != vodAdsHash) {
            vodAdsHash = vodHash;
            vodPatterns = compilePatterns(vodAds);
        }
        if (liveHash != liveAdsHash) {
            liveAdsHash = liveHash;
            livePatterns = compilePatterns(liveAds);
        }
    }

    private static List<Pattern> compilePatterns(List<String> ads) {
        if (ads.isEmpty()) return Collections.emptyList();
        List<Pattern> patterns = new ArrayList<>(ads.size());
        for (String ad : ads) {
            if (TextUtils.isEmpty(ad)) continue;
            try {
                patterns.add(Pattern.compile(ad, Pattern.CASE_INSENSITIVE));
            } catch (Exception e) {
                patterns.add(Pattern.compile(Pattern.quote(ad), Pattern.CASE_INSENSITIVE));
            }
        }
        return patterns;
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
            if (token.startsWith("ads") || token.startsWith("adv") || token.startsWith("advert")) return true;
            if (token.startsWith("adbreak") || token.startsWith("adpod")) return true;
        }
        return false;
    }
}
