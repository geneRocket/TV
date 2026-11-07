package com.fongmi.android.tv.utils;

import android.text.TextUtils;

import com.fongmi.android.tv.bean.Vod;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.function.Supplier;

public final class SearchSorter {

    private static final Pattern TITLE_SEPARATORS = Pattern.compile("[\\p{P}\\p{Z}\\s]+");
    private static final String QUALITY_LABEL = "(?:4k|8k|2160p|1080p|720p|uhd|hdr|蓝光|超清|高清|原盘|完整版)";
    private static final Pattern TITLE_QUALITY = Pattern.compile("(?i)[\\p{P}\\p{Z}\\s&&[^\\p{Pe}\\p{Pf}]]+" + QUALITY_LABEL
            + "(?:[\\p{P}\\p{Z}\\s]+" + QUALITY_LABEL + ")*[\\p{P}\\p{Z}\\s]*$");
    private static final Pattern TITLE_BRACKETED_YEAR = Pattern.compile("[\\p{Z}\\s]*[（(\\[【{][\\p{Z}\\s]*((?:19|20)\\d{2})[\\p{Z}\\s]*[）)\\]】}][\\p{Z}\\s]*$");

    private SearchSorter() {
    }

    public static List<Vod> merge(List<Vod> current, List<Vod> incoming, String keyword) {
        List<Vod> result = new ArrayList<>();
        Set<String> loaded = new HashSet<>();
        String normalizedKeyword = Util.normalize(keyword);
        if (current != null) {
            for (Vod item : current) {
                String key = key(item);
                if (key.isEmpty() || !loaded.add(key)) continue;
                prepareNormalized(item, normalizedKeyword);
                result.add(item);
            }
        }
        if (incoming != null) {
            for (Vod item : incoming) {
                String key = key(item);
                if (key.isEmpty() || !loaded.add(key)) continue;
                prepareNormalized(item, normalizedKeyword);
                result.add(item);
            }
        }
        result.sort(comparator(keyword, ""));
        return result;
    }

    public static void sort(List<Vod> items, String keyword) {
        if (items == null || items.size() < 2) return;
        String normalizedKeyword = Util.normalize(keyword);
        for (Vod item : items) prepareNormalized(item, normalizedKeyword);
        items.sort(comparator(keyword, ""));
    }

    public static Comparator<Vod> comparator(String keyword, String actor) {
        String targetActor = Objects.toString(actor, "");
        String[] targetActors = splitActors(targetActor);
        Map<Vod, Integer> actorRanks = new IdentityHashMap<>();
        return (left, right) -> comparePrepared(left, right,
                actorRanks.computeIfAbsent(left, item -> actorRank(item, targetActor, targetActors)),
                actorRanks.computeIfAbsent(right, item -> actorRank(item, targetActor, targetActors)));
    }

    public static int compare(Vod left, Vod right, String keyword, String actor) {
        String targetActor = Objects.toString(actor, "");
        String[] targetActors = splitActors(targetActor);
        return comparePrepared(left, right, actorRank(left, targetActor, targetActors), actorRank(right, targetActor, targetActors));
    }

    /**
     * Strict matching used by automatic source switching. Metadata suffixes are ignored, but
     * semantic additions and sequel numbers are retained to avoid switching to another title.
     */
    public static boolean matchesTitle(String source, String target) {
        String normalizedSource = normalizeTitle(source);
        String normalizedTarget = normalizeTitle(target);
        return !normalizedSource.isEmpty() && normalizedSource.equals(normalizedTarget);
    }

    public static boolean matchesDetailFallback(String title, String candidate, String year) {
        if (!matchesTitle(title, candidate)) return false;
        Matcher expected = TITLE_BRACKETED_YEAR.matcher(TITLE_QUALITY.matcher(title).replaceFirst(""));
        if (!expected.find()) return true;
        Matcher embedded = TITLE_BRACKETED_YEAR.matcher(TITLE_QUALITY.matcher(candidate).replaceFirst(""));
        if (embedded.find() && !expected.group(1).equals(embedded.group(1))) return false;
        return year == null || !year.matches("(?:19|20)\\d{2}") || expected.group(1).equals(year);
    }

    /** Automatic replacement requires the same title; manual suggestions retain fuzzy ranking. */
    public static boolean isSourceCandidate(Vod item, String keyword, boolean automatic, double minimumScore) {
        if (item == null) return false;
        prepare(item, keyword);
        return automatic ? matchesTitle(item.getVodName(), keyword) : item.getScore() >= minimumScore;
    }

    /** Polls the existing candidate pool for an automatic handoff, not a manual click. */
    public static Vod pollMatchingSource(Supplier<Vod> poll, String title) {
        Vod item;
        while ((item = poll.get()) != null) {
            if (matchesTitle(item.getVodName(), title)) return item;
        }
        return null;
    }

    private static String normalizeTitle(String value) {
        if (value == null) return "";
        return TITLE_SEPARATORS.matcher(detailFallbackKeyword(value).toLowerCase(Locale.ROOT)).replaceAll(" ").trim();
    }

    public static String detailFallbackKeyword(String title) {
        String normalized = title.trim();
        String previous;
        do {
            previous = normalized;
            normalized = TITLE_BRACKETED_YEAR.matcher(normalized).replaceFirst("").trim();
            normalized = TITLE_QUALITY.matcher(normalized).replaceFirst("").trim();
        } while (!normalized.equals(previous));
        return normalized;
    }

    private static int comparePrepared(Vod left, Vod right, int leftActorRank, int rightActorRank) {
        int result = Double.compare(right.getScore(), left.getScore());
        if (result != 0) return result;
        result = Integer.compare(leftActorRank, rightActorRank);
        if (result != 0) return result;
        result = Long.compare(left.getInsertTimestamp(), right.getInsertTimestamp());
        if (result != 0) return result;
        return key(left).compareToIgnoreCase(key(right));
    }

    public static String key(Vod item) {
        if (item == null) return "";
        return key(item.getSiteKey(), item.getVodId(), item.getVodName());
    }

    public static String key(String site, String id, String name) {
        String normalizedSite = site == null ? "" : site.trim();
        String normalizedId = id == null ? "" : id.trim();
        if (normalizedSite.isEmpty()) return "";
        String normalizedName = Util.normalize(name);
        if (normalizedId.isEmpty() && normalizedName.isEmpty()) return "";
        return normalizedSite + "@" + (normalizedId.isEmpty() ? normalizedName : normalizedId);
    }

    public static void prepare(Vod item, String keyword) {
        prepareNormalized(item, Util.normalize(keyword));
    }

    private static void prepareNormalized(Vod item, String normalizedKeyword) {
        if (item == null) return;
        String name = Util.normalize(item.getVodName());
        item.setScore(Util.similarity(name, normalizedKeyword));
        if (item.getInsertTimestamp() == 0) item.setInsertTimestamp(System.currentTimeMillis());
    }

    private static int actorRank(Vod item, String targetActor, String[] targetActors) {
        String source = item == null ? "" : item.getVodActor();
        if (targetActors.length == 0 || TextUtils.isEmpty(source)) return 2;
        String[] sourceActors = splitActors(source);
        if (Util.similarity(source, targetActor) >= 0.8) return 0;
        return hasActorOverlap(sourceActors, targetActors) ? 1 : 3;
    }

    private static boolean hasActorOverlap(String[] sourceActors, String[] targetActors) {
        for (String sourceActor : sourceActors) {
            if (sourceActor.isEmpty()) continue;
            for (String targetActor : targetActors) {
                if (targetActor.isEmpty()) continue;
                if (sourceActor.equals(targetActor)) return true;
            }
        }
        return false;
    }

    private static String[] splitActors(String text) {
        String value = Objects.toString(text, "").trim().toLowerCase(Locale.ROOT);
        return value.isEmpty() ? new String[0] : value.split("[\\s,，、/／;；|｜]+");
    }
}
