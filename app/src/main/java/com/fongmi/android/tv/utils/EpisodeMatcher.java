package com.fongmi.android.tv.utils;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class EpisodeMatcher {

    private static final Pattern SEASON_EPISODE = Pattern.compile("(?i)(?:^|\\b)S(?:EASON)?\\s*0*(\\d{1,3})\\s*[-._ ]*E(?:P(?:ISODE)?)?\\s*0*(\\d{1,4})(?:\\b|$)");
    private static final Pattern CHINESE_EPISODE = Pattern.compile("第?\\s*0*(\\d{1,4})\\s*[集話话期]");
    private static final Pattern CHINESE_NUMBER_EPISODE = Pattern.compile("第?\\s*([零〇一二兩两三四五六七八九十百千]+)\\s*[集話话期]");
    private static final Pattern ENGLISH_EPISODE = Pattern.compile("(?i)(?:^|\\b)E(?:P(?:ISODE)?)?\\s*0*(\\d{1,4})(?:\\b|$)");
    private static final Pattern PLAIN_NUMBER = Pattern.compile("^0*(\\d{1,4})$");
    private static final Pattern PLAIN_CHINESE_NUMBER = Pattern.compile("^([零〇一二兩两三四五六七八九十百千]+)$");
    private static final Pattern SEASON = Pattern.compile("(?i)(?:^|\\b)S(?:EASON)?\\s*0*(\\d{1,3})(?:\\b|$)");

    private EpisodeMatcher() {
    }

    public static int find(List<String> names, String target, boolean strict, int position) {
        if (names == null || names.isEmpty()) return -1;
        String normalizedTarget = normalize(target);
        if (!normalizedTarget.isEmpty()) {
            for (int i = 0; i < names.size(); i++) if (normalize(names.get(i)).equals(normalizedTarget)) return i;
            EpisodeNumber targetNumber = numberOf(target);
            if (targetNumber != null) {
                int numberMatch = uniqueNumber(names, targetNumber);
                if (numberMatch != -1) return numberMatch;
            }
            int partMatch = uniquePart(names, partOf(normalizedTarget));
            if (partMatch != -1) return partMatch;
            int fuzzy = uniqueContains(names, normalizedTarget);
            if (fuzzy != -1) return fuzzy;
        }
        if (strict) return -1;
        return position >= 0 && position < names.size() ? position : 0;
    }

    private static int uniqueNumber(List<String> names, EpisodeNumber target) {
        int match = -1;
        for (int i = 0; i < names.size(); i++) {
            if (!target.matches(numberOf(names.get(i)))) continue;
            if (match != -1) return -1;
            match = i;
        }
        return match;
    }

    private static int uniquePart(List<String> names, int target) {
        if (target == 0) return -1;
        int match = -1;
        for (int i = 0; i < names.size(); i++) {
            if (partOf(normalize(names.get(i))) != target) continue;
            if (match != -1) return -1;
            match = i;
        }
        return match;
    }

    private static int uniqueContains(List<String> names, String target) {
        if (target.codePointCount(0, target.length()) < 2) return -1;
        int match = -1;
        for (int i = 0; i < names.size(); i++) {
            String name = normalize(names.get(i));
            if (name.isEmpty() || !containsAtNumberBoundary(name, target) && !containsAtNumberBoundary(target, name)) continue;
            if (match != -1) return -1;
            match = i;
        }
        return match;
    }

    private static boolean containsAtNumberBoundary(String value, String part) {
        for (int from = 0; from <= value.length() - part.length();) {
            int start = value.indexOf(part, from);
            if (start < 0) return false;
            int end = start + part.length();
            boolean joinsBefore = start > 0 && Character.isDigit(part.charAt(0)) && Character.isDigit(value.charAt(start - 1));
            boolean joinsAfter = end < value.length() && Character.isDigit(part.charAt(part.length() - 1)) && Character.isDigit(value.charAt(end));
            if (!joinsBefore && !joinsAfter) return true;
            from = start + 1;
        }
        return false;
    }

    private static String normalize(String value) {
        if (value == null) return "";
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFKC);
        String lower = normalized.trim().toLowerCase(Locale.ROOT);
        StringBuilder result = new StringBuilder(lower.length());
        for (int i = 0; i < lower.length(); i++) {
            char current = lower.charAt(i);
            if (Character.isWhitespace(current)) continue;
            if (isPunctuation(current)) {
                boolean betweenDigits = hasDigitBefore(lower, i) && hasDigitAfter(lower, i);
                if (betweenDigits) result.append('-');
                continue;
            }
            result.append(current);
        }
        return result.toString();
    }

    private static boolean hasDigitBefore(String value, int index) {
        for (int i = index - 1; i >= 0; i--) {
            if (Character.isWhitespace(value.charAt(i))) continue;
            return Character.isDigit(value.charAt(i));
        }
        return false;
    }

    private static boolean hasDigitAfter(String value, int index) {
        for (int i = index + 1; i < value.length(); i++) {
            if (Character.isWhitespace(value.charAt(i))) continue;
            return Character.isDigit(value.charAt(i));
        }
        return false;
    }

    private static boolean isPunctuation(char value) {
        int type = Character.getType(value);
        return type == Character.CONNECTOR_PUNCTUATION
                || type == Character.DASH_PUNCTUATION
                || type == Character.START_PUNCTUATION
                || type == Character.END_PUNCTUATION
                || type == Character.INITIAL_QUOTE_PUNCTUATION
                || type == Character.FINAL_QUOTE_PUNCTUATION
                || type == Character.OTHER_PUNCTUATION
                || value == '·' || value == '•';
    }

    private static EpisodeNumber numberOf(String value) {
        if (value == null) return null;
        value = Normalizer.normalize(value, Normalizer.Form.NFKC);
        Matcher combined = SEASON_EPISODE.matcher(value);
        if (combined.find()) return new EpisodeNumber(parse(combined.group(1)), parse(combined.group(2)));
        Integer episode = find(CHINESE_EPISODE, value);
        if (episode == null) episode = findChinese(CHINESE_NUMBER_EPISODE, value);
        if (episode == null) episode = find(ENGLISH_EPISODE, value);
        if (episode == null) {
            episode = find(PLAIN_NUMBER, value.trim());
            if (episode != null && episode >= 1900 && episode <= 2199) episode = null;
        }
        if (episode == null) episode = findChinese(PLAIN_CHINESE_NUMBER, value.trim());
        if (episode == null) return null;
        Integer season = find(SEASON, value);
        return new EpisodeNumber(season, episode);
    }

    private static Integer find(Pattern pattern, String value) {
        Matcher matcher = pattern.matcher(value);
        return matcher.find() ? parse(matcher.group(1)) : null;
    }

    private static Integer findChinese(Pattern pattern, String value) {
        Matcher matcher = pattern.matcher(value);
        return matcher.find() ? parseChinese(matcher.group(1)) : null;
    }

    private static Integer parseChinese(String value) {
        if (value == null || value.isEmpty()) return null;
        boolean hasUnit = value.indexOf('十') >= 0 || value.indexOf('百') >= 0 || value.indexOf('千') >= 0;
        int result = 0;
        int digit = 0;
        for (int i = 0; i < value.length(); i++) {
            int number = chineseDigit(value.charAt(i));
            if (number >= 0) {
                if (hasUnit) digit = number;
                else result = result * 10 + number;
                continue;
            }
            int unit = chineseUnit(value.charAt(i));
            if (unit == 0) return null;
            result += (digit == 0 ? 1 : digit) * unit;
            digit = 0;
        }
        return hasUnit ? result + digit : result;
    }

    private static int chineseDigit(char value) {
        switch (value) {
            case '零': case '〇': return 0;
            case '一': return 1;
            case '二': case '兩': case '两': return 2;
            case '三': return 3;
            case '四': return 4;
            case '五': return 5;
            case '六': return 6;
            case '七': return 7;
            case '八': return 8;
            case '九': return 9;
            default: return -1;
        }
    }

    private static int chineseUnit(char value) {
        if (value == '十') return 10;
        if (value == '百') return 100;
        if (value == '千') return 1000;
        return 0;
    }

    private static int partOf(String value) {
        if ("上".equals(value) || "上集".equals(value) || "上部".equals(value) || "上篇".equals(value) || "前篇".equals(value)) return 1;
        if ("中".equals(value) || "中集".equals(value) || "中部".equals(value) || "中篇".equals(value)) return 2;
        if ("下".equals(value) || "下集".equals(value) || "下部".equals(value) || "下篇".equals(value) || "后篇".equals(value) || "後篇".equals(value)) return 3;
        return 0;
    }

    private static Integer parse(String value) {
        try {
            return Integer.parseInt(value);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static final class EpisodeNumber {

        private final Integer season;
        private final Integer episode;

        private EpisodeNumber(Integer season, Integer episode) {
            this.season = season;
            this.episode = episode;
        }

        private boolean matches(EpisodeNumber other) {
            if (other == null || episode == null || !episode.equals(other.episode)) return false;
            return season == null || other.season == null || season.equals(other.season);
        }
    }
}
