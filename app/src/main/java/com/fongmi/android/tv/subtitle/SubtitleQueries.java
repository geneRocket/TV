package com.fongmi.android.tv.subtitle;

import java.util.*;
import java.util.function.UnaryOperator;
import java.util.regex.Pattern;

/** Bounded aliases, without guessing movie translations or dropping sequel numbers. */
public final class SubtitleQueries {
    private static final Pattern LABEL = Pattern.compile("(?i)[\\s._-]*(?:[\\[（(](?:粤语|粵語|国语|國語|国粤双语|國粵雙語|中字|中文字幕|简体|簡體|繁体|繁體|高清|蓝光|藍光|修复版|修復版|[24]K|HD|BD|(?:1080|2160|720)P)[\\]）)]|(?:粤语|粵語|国语|國語)版?)$");
    private final UnaryOperator<String> simplified, traditional;
    public SubtitleQueries(UnaryOperator<String> simplified, UnaryOperator<String> traditional) {
        this.simplified = simplified; this.traditional = traditional;
    }
    public static SubtitleQueries identity() { return new SubtitleQueries(value -> value, value -> value); }
    public List<String> variants(String title) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        String original = title == null ? "" : title.trim();
        add(values, original);
        String cleaned = clean(original);
        add(values, cleaned); add(values, simplified.apply(cleaned)); add(values, traditional.apply(cleaned));
        return Collections.unmodifiableList(new ArrayList<>(values));
    }
    public static String clean(String title) {
        String value = title == null ? "" : title.trim(), previous;
        do {
            previous = value;
            value = LABEL.matcher(SubtitleRanking.simplify(value)).replaceFirst("").trim();
        } while (!previous.equals(value));
        return value.isEmpty() ? (title == null ? "" : title.trim()) : value;
    }
    private static void add(Set<String> values, String value) {
        if (value != null && !value.trim().isEmpty()) values.add(value.trim());
    }
}
