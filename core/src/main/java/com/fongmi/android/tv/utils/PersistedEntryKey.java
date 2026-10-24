package com.fongmi.android.tv.utils;

import java.util.Locale;

/** Codec for the existing site@@@opaque-video-id@@@config-id storage format. */
public final class PersistedEntryKey {
    public static final String SEPARATOR = "@@@";
    private PersistedEntryKey() {}

    public static String site(String key) {
        if (key == null) return "";
        int end = key.indexOf(SEPARATOR);
        return end < 0 ? key : key.substring(0, end);
    }

    public static String video(String key) {
        if (key == null) return "";
        int first = key.indexOf(SEPARATOR);
        if (first < 0) return "";
        int last = key.lastIndexOf(SEPARATOR);
        return key.substring(first + SEPARATOR.length(), last > first ? last : key.length());
    }

    public static int config(String key, int fallback) {
        if (key != null) {
            int first = key.indexOf(SEPARATOR), last = key.lastIndexOf(SEPARATOR);
            if (last > first) {
                try { return Integer.parseInt(key.substring(last + SEPARATOR.length())); }
                catch (NumberFormatException ignored) {}
            }
            String site = site(key);
            int split = site.indexOf('@');
            if (split > 0) {
                try { return Integer.parseInt(site.substring(0, split)); }
                catch (NumberFormatException ignored) {}
            }
        }
        return fallback;
    }

    public static String rawSite(String site) {
        if (site == null) return "";
        int split = site.indexOf('@');
        if (split <= 0) return site;
        for (int i = 0; i < split; i++) if (!Character.isDigit(site.charAt(i))) return site;
        try { Integer.parseInt(site.substring(0, split)); return site.substring(split + 1); }
        catch (NumberFormatException ignored) { return site; }
    }

    public static String create(String site, String id, String name, int config) {
        String video = id == null || id.isEmpty()
                ? (name == null ? "" : name.toLowerCase(Locale.ROOT).replaceAll("[\\p{Punct}\\s]+", " ").trim()) : id;
        return rawSite(site) + SEPARATOR + video + SEPARATOR + config;
    }

    public static String rebase(String key, int config) {
        if (key == null || !key.contains(SEPARATOR)) return key;
        return rawSite(site(key)) + SEPARATOR + video(key) + SEPARATOR + config;
    }
}
