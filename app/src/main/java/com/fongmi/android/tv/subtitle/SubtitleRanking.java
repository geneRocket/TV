package com.fongmi.android.tv.subtitle;

import java.util.Locale;
import java.util.regex.Pattern;
import java.util.*;
import com.fongmi.android.tv.bean.Subtitle;

/** Shared query normalization and stable provider result ranking. */
public final class SubtitleRanking {
    private SubtitleRanking() {}
    private static final Pattern REGEX_EPISODE = Pattern.compile("(?i)(?:[\\[（(]?(?:s\\d{1,2}e?\\d{0,3}|e\\d{1,3}|第\\s*\\d{1,4}\\s*[集话季]|\\d{1,4}\\s*[集话])[\\]）)]?|[\\s._-]+(?:1080|720|2160|4k|web[- ]?dl|bluray|x26[45]|h\\.?26[45]|hevc|aac|hdr|dv)[\\w.-]*)$");

    public static String simplify(String keyword) {
        String result = keyword == null ? "" : keyword.trim();
        String previous;
        do {
            previous = result;
            result = REGEX_EPISODE.matcher(result).replaceFirst("").trim();
        } while (!java.util.Objects.equals(previous, result));
        return result.replaceAll("[\\s._-]+$", "").trim();
    }

    public static int score(String name, String keyword) {
        String title = normalizeForMatch(name);
        String query = normalizeForMatch(keyword);
        if (title.equals(query)) return 0;
        if (title.startsWith(query)) return 1;
        if (title.contains(query)) return 2;
        String simplified = normalizeForMatch(simplify(keyword));
        return !com.fongmi.android.tv.utils.TextValues.isEmpty(simplified) && title.contains(simplified) ? 3 : 4;
    }

    public static String normalizeForMatch(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("[\\s._\\-\\[\\]（）()]+", "");
    }

    public static List<Subtitle> order(List<Subtitle> items, List<String> aliases, SubtitlePreference preference) {
        List<Subtitle> ordered = new ArrayList<>(items);
        ordered.sort(Comparator.comparingInt((Subtitle item) -> Math.min(titleScore(item.getName(), aliases),titleScore(item.getMovieTitle(),aliases)))
                .thenComparingInt(item -> languageScore(item, preference)));
        return ordered;
    }

    private static int titleScore(String name, List<String> aliases) {
        int best = 4;
        for (String alias : aliases) best = Math.min(best, score(name, alias));
        return best;
    }

    private static int languageScore(Subtitle item, SubtitlePreference preference) {
        String text = (item.getLanguage() + " " + item.getName()).toLowerCase(Locale.ROOT);
        boolean cantonese = text.matches("(?s).*(?:粤|粵|cantonese|zh[-_]hk).*"),
                traditional = text.matches("(?s).*(?:繁|traditional|cht|zh[-_](?:hant|tw)).*"),
                chinese = cantonese || traditional || text.matches("(?s).*(?:简|簡|中文|chinese|chs|zh[-_]cn).*"),
                english = text.matches("(?s).*(?:英语|英語|english|\\beng\\b).*"),
                bilingual = text.contains("双语") || text.contains("雙語") || (chinese && english);
        switch (preference) {
            case ALL: return 0;
            case CANTONESE: return cantonese ? 0 : chinese ? 1 : 2;
            case TRADITIONAL: return traditional ? 0 : chinese ? 1 : 2;
            case BILINGUAL: return bilingual ? 0 : chinese ? 1 : 2;
            default: return chinese ? 0 : 1;
        }
    }
}
