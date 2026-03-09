package com.fongmi.android.tv.utils;

import android.net.Uri;
import android.text.TextUtils;

import com.github.catvod.utils.UriUtil;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class M3u8AdFilter {

    private static final int SUBTITLE_WHITELIST_MAX = 512;
    private static final Set<String> SUBTITLE_PLAYLIST_WHITELIST = Collections.newSetFromMap(new LinkedHashMap<String, Boolean>(SUBTITLE_WHITELIST_MAX, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
            return size() > SUBTITLE_WHITELIST_MAX;
        }
    });

    private M3u8AdFilter() {
    }

    public static boolean isLikelyM3u8(String contentType, String url) {
        String ct = contentType == null ? "" : contentType.toLowerCase(Locale.US);
        return ct.contains("mpegurl") || ct.contains("vnd.apple.mpegurl") || (!TextUtils.isEmpty(url) && url.contains(".m3u8"));
    }

    public static byte[] filterMinorHost(byte[] bytes, String baseUrl) {
        String content = new String(bytes, StandardCharsets.UTF_8);
        int headerIndex = findM3uHeaderIndex(content);
        if (headerIndex < 0) return bytes;
        if (headerIndex > 0) content = content.substring(headerIndex);
        if (isSubtitleWhitelisted(baseUrl)) return toBytesOrOriginal(content, bytes);
        registerSubtitlePlaylists(content, baseUrl);
        if (!content.contains("#EXTINF")) return toBytesOrOriginal(content, bytes);
        List<Record> records = parseRecords(content, baseUrl);
        if (isSubtitlePlaylist(content, records)) return toBytesOrOriginal(content, bytes);
        records = filterAdTagSegments(records);
        String filtered = filterMinorHostSegments(records, content);
        if (!startsWithM3uHeader(filtered)) return toBytesOrOriginal(content, bytes);
        return toBytesOrOriginal(filtered, bytes);
    }

    private static boolean startsWithM3uHeader(String content) {
        return findM3uHeaderIndex(content) == 0;
    }

    private static int findM3uHeaderIndex(String content) {
        if (TextUtils.isEmpty(content)) return -1;
        int i = 0;
        if (!content.isEmpty() && content.charAt(0) == '\uFEFF') i = 1;
        while (i < content.length()) {
            char c = content.charAt(i);
            if (c == ' ' || c == '\t' || c == '\r' || c == '\n') i++;
            else break;
        }
        if (content.regionMatches(i, "#EXTM3U", 0, 7)) return i;
        return -1;
    }

    private static byte[] toBytesOrOriginal(String content, byte[] original) {
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        if (bytes.length == original.length) {
            boolean same = true;
            for (int i = 0; i < bytes.length; i++) {
                if (bytes[i] != original[i]) {
                    same = false;
                    break;
                }
            }
            if (same) return original;
        }
        return bytes;
    }

    private static void registerSubtitlePlaylists(String content, String baseUrl) {
        if (!content.contains("#EXT-X-MEDIA")) return;
        String[] lines = content.split("\n", -1);
        for (String raw : lines) {
            String line = raw.endsWith("\r") ? raw.substring(0, raw.length() - 1) : raw;
            if (!line.startsWith("#EXT-X-MEDIA:")) continue;
            String type = parseAttributeString(line, "TYPE");
            if (!"SUBTITLES".equalsIgnoreCase(type) && !"CLOSED-CAPTIONS".equalsIgnoreCase(type)) continue;
            String uri = parseAttributeString(line, "URI");
            addSubtitleWhitelist(baseUrl, uri);
        }
    }

    private static void addSubtitleWhitelist(String baseUrl, String uri) {
        if (TextUtils.isEmpty(uri)) return;
        String resolved = resolveUri(baseUrl, uri);
        if (TextUtils.isEmpty(resolved)) return;
        synchronized (SUBTITLE_PLAYLIST_WHITELIST) {
            SUBTITLE_PLAYLIST_WHITELIST.add(normalizeForMatch(resolved));
        }
    }

    private static boolean isSubtitleWhitelisted(String url) {
        if (TextUtils.isEmpty(url)) return false;
        String full = normalizeForMatch(url);
        synchronized (SUBTITLE_PLAYLIST_WHITELIST) {
            return SUBTITLE_PLAYLIST_WHITELIST.contains(full);
        }
    }

    private static String normalizeForMatch(String url) {
        String lower = url.toLowerCase(Locale.US).trim();
        int q = lower.indexOf('?');
        if (q >= 0) lower = lower.substring(0, q);
        int h = lower.indexOf('#');
        if (h >= 0) lower = lower.substring(0, h);
        return lower;
    }

    private static String resolveUri(String baseUrl, String uri) {
        try {
            return UriUtil.resolve(baseUrl, uri);
        } catch (Throwable ignored) {
            return uri;
        }
    }

    private static boolean isSubtitlePlaylist(String content, List<Record> records) {
        String lowerContent = content.toLowerCase(Locale.US);
        // 启发式判断，增强字幕保护
        if (lowerContent.startsWith("webvtt") || lowerContent.contains("\nwebvtt")) return true;
        if (lowerContent.contains("type=subtitles") || lowerContent.contains("vtt") || lowerContent.contains("subtitle") || lowerContent.contains("caption")) return true;

        for (Record record : records) {
            if (isSubtitleUri(record.line)) return true;
            if (record.tags == null) continue;
            for (String tag : record.tags) {
                if (isSubtitleMapTag(tag)) return true;
            }
        }
        return false;
    }

    private static boolean isSubtitleMapTag(String tag) {
        if (!tag.startsWith("#EXT-X-MAP:")) return false;
        String uri = parseAttributeString(tag, "URI");
        return isSubtitleUri(uri);
    }

    private static boolean isSubtitleUri(String uri) {
        if (TextUtils.isEmpty(uri)) return false;
        String lower = uri.toLowerCase(Locale.US);
        int query = lower.indexOf('?');
        if (query >= 0) lower = lower.substring(0, query);
        int hash = lower.indexOf('#');
        if (hash >= 0) lower = lower.substring(0, hash);
        return lower.endsWith(".vtt")
                || lower.endsWith(".webvtt")
                || lower.endsWith(".ttml")
                || lower.endsWith(".dfxp")
                || lower.endsWith(".srt")
                || lower.endsWith(".ass")
                || lower.endsWith(".ssa")
                || lower.endsWith(".smi")
                || lower.endsWith(".sub")
                || lower.endsWith(".scc")
                || lower.endsWith(".xml");
    }

    private static String filterMinorHostSegments(List<Record> records, String original) {
        Map<String, Integer> hostCount = new HashMap<>();
        int segmentCount = 0;
        for (Record record : records) {
            if (!record.segment) continue;
            segmentCount++;
            // 修复：将空 Host 计入统计，防止相对路径视频的广告过滤失效
            String h = record.host == null ? "" : record.host;
            hostCount.put(h, hostCount.getOrDefault(h, 0) + 1);
        }
        if (segmentCount < 2 || hostCount.size() < 2) return build(records, original.length());

        String majorHost = null;
        int majorCount = -1;
        for (Map.Entry<String, Integer> entry : hostCount.entrySet()) {
            if (entry.getValue() > majorCount) {
                majorHost = entry.getKey();
                majorCount = entry.getValue();
            }
        }

        // 修复：判断 null 而不是 TextUtils.isEmpty(majorHost)，允许空字符串成为 majorHost
        if (majorHost == null) return build(records, original.length());
        if ((majorCount * 10) < (segmentCount * 6)) return build(records, original.length());

        StringBuilder sb = new StringBuilder(original.length());
        for (Record record : records) {
            if (!record.segment) {
                sb.append(record.line).append('\n');
            } else {
                String h = record.host == null ? "" : record.host;
                if (TextUtils.isEmpty(h) || majorHost.equals(h)) {
                    for (String tag : record.tags) sb.append(tag).append('\n');
                    sb.append(record.line).append('\n');
                }
            }
        }
        if (sb.length() > 0) sb.setLength(sb.length() - 1);
        return sb.toString();
    }

    private static List<Record> filterAdTagSegments(List<Record> records) {
        List<Record> kept = new ArrayList<>(records.size());
        boolean cueAdOpen = false;
        double cueAdSeconds = 0;
        double dateRangeAdSeconds = 0;
        boolean discontinuityAdOpen = false;
        int discontinuityAdSegments = 0;
        for (Record record : records) {
            if (!record.segment) {
                if (isCueOutTag(record.line)) {
                    cueAdOpen = true;
                    cueAdSeconds = Math.max(cueAdSeconds, parseCueOutDuration(record.line));
                    continue;
                }
                if (isCueInTag(record.line)) {
                    cueAdOpen = false;
                    cueAdSeconds = 0;
                    dateRangeAdSeconds = 0;
                    continue;
                }
                if (isAdDateRangeTag(record.line)) {
                    double duration = parseDateRangeDuration(record.line);
                    if (duration > 0) dateRangeAdSeconds = Math.max(dateRangeAdSeconds, duration);
                    continue;
                }
                if (record.line.startsWith("#EXT-X-CUE-")) continue;
                kept.add(record);
                continue;
            }

            boolean explicitAd = isExplicitAdRecord(record);
            boolean hasDiscontinuity = hasTagPrefix(record.tags, "#EXT-X-DISCONTINUITY");
            if (explicitAd && (hasDiscontinuity || hasAdSignalTag(record.tags))) {
                discontinuityAdOpen = true;
                discontinuityAdSegments = 0;
            }
            if (discontinuityAdOpen) {
                if (!explicitAd && discontinuityAdSegments > 0 && hasDiscontinuity) {
                    discontinuityAdOpen = false;
                    discontinuityAdSegments = 0;
                } else {
                    discontinuityAdSegments++;
                    continue;
                }
            }
            if (explicitAd) continue;
            if (cueAdOpen || dateRangeAdSeconds > 0) {
                if (cueAdOpen && cueAdSeconds > 0) {
                    cueAdSeconds = nextRemaining(cueAdSeconds, record.duration);
                    if (cueAdSeconds <= 0) cueAdOpen = false;
                }
                if (dateRangeAdSeconds > 0) dateRangeAdSeconds = nextRemaining(dateRangeAdSeconds, record.duration);
                continue;
            }
            kept.add(record);
        }
        return kept;
    }

    private static double nextRemaining(double remaining, double segmentDuration) {
        if (remaining <= 0) return 0;
        if (segmentDuration > 0) return Math.max(0, remaining - segmentDuration);
        return remaining;
    }

    private static List<Record> parseRecords(String content, String baseUrl) {
        String[] lines = content.split("\n", -1);
        List<Record> records = new ArrayList<>(lines.length);
        List<String> pending = new ArrayList<>();

        for (String raw : lines) {
            String line = raw.endsWith("\r") ? raw.substring(0, raw.length() - 1) : raw;
            if (isSegmentTag(line) || (!pending.isEmpty() && isSegmentFollowTag(line))) {
                pending.add(line);
                continue;
            }
            if (!pending.isEmpty()) {
                if (!line.startsWith("#") && !line.trim().isEmpty()) {
                    records.add(Record.segment(new ArrayList<>(pending), line, resolveHost(baseUrl, line), parseExtInfDuration(pending)));
                    pending.clear();
                    continue;
                }
                for (String tag : pending) records.add(Record.plain(tag));
                pending.clear();
            }
            records.add(Record.plain(line));
        }

        if (!pending.isEmpty()) for (String tag : pending) records.add(Record.plain(tag));
        return records;
    }

    private static boolean isSegmentTag(String line) {
        return line.startsWith("#EXTINF") || line.startsWith("#EXT-X-DISCONTINUITY") || line.startsWith("#EXT-X-PROGRAM-DATE-TIME");
    }

    private static boolean isSegmentFollowTag(String line) {
        return line.startsWith("#EXT-X-BYTERANGE");
    }

    private static double parseExtInfDuration(List<String> tags) {
        for (String tag : tags) {
            if (!tag.startsWith("#EXTINF:")) continue;
            int start = "#EXTINF:".length();
            int end = tag.indexOf(',', start);
            String value = end > start ? tag.substring(start, end) : tag.substring(start);
            return parseDouble(value);
        }
        return 0;
    }

    private static boolean isCueOutTag(String line) {
        return line.startsWith("#EXT-X-CUE-OUT");
    }

    private static boolean isCueInTag(String line) {
        return line.startsWith("#EXT-X-CUE-IN");
    }

    private static boolean isAdDateRangeTag(String line) {
        if (!line.startsWith("#EXT-X-DATERANGE")) return false;
        String lower = line.toLowerCase(Locale.US);
        if (lower.contains("scte35")) return true;
        String id = parseAttributeString(line, "ID");
        String type = parseAttributeString(line, "CLASS");
        return containsAdKeyword(id) || containsAdKeyword(type);
    }

    private static double parseCueOutDuration(String line) {
        int index = line.indexOf(':');
        if (index > -1 && index < line.length() - 1) {
            String part = line.substring(index + 1);
            if (!part.contains("=")) return parseDouble(part);
        }
        double duration = parseAttributeDouble(line, "DURATION");
        if (duration > 0) return duration;
        return parseAttributeDouble(line, "PLANNED-DURATION");
    }

    private static double parseDateRangeDuration(String line) {
        double duration = parseAttributeDouble(line, "DURATION");
        if (duration > 0) return duration;
        return parseAttributeDouble(line, "PLANNED-DURATION");
    }

    private static int indexOfKey(String line, String key) {
        String token = key + "=";
        int idx = line.indexOf(token);
        while (idx >= 0) {
            if (idx == 0 || line.charAt(idx - 1) == ',' || line.charAt(idx - 1) == ':' || line.charAt(idx - 1) == ' ') {
                return idx;
            }
            idx = line.indexOf(token, idx + 1);
        }
        return -1;
    }

    private static double parseAttributeDouble(String line, String key) {
        int idx = indexOfKey(line, key);
        if (idx < 0) return 0;
        int start = idx + key.length() + 1;
        int end = start;
        boolean quoted = start < line.length() && line.charAt(start) == '"';
        if (quoted) {
            start++;
            end = line.indexOf('"', start);
            if (end < 0) end = line.length();
        } else {
            while (end < line.length() && line.charAt(end) != ',' && line.charAt(end) != '\r' && line.charAt(end) != '\n') end++;
        }
        return parseDouble(line.substring(start, end).trim());
    }

    private static String parseAttributeString(String line, String key) {
        int idx = indexOfKey(line, key);
        if (idx < 0) return "";
        int start = idx + key.length() + 1;
        int end = start;
        boolean quoted = start < line.length() && line.charAt(start) == '"';
        if (quoted) {
            start++;
            end = line.indexOf('"', start);
            if (end < 0) end = line.length();
        } else {
            // 修复：确保过滤可能遗留的换行符
            while (end < line.length() && line.charAt(end) != ',' && line.charAt(end) != '\r' && line.charAt(end) != '\n') end++;
        }
        return line.substring(start, end).trim();
    }

    private static boolean containsAdKeyword(String text) {
        if (TextUtils.isEmpty(text)) return false;
        String value = text.toLowerCase(Locale.US).replace('-', ' ').replace('_', ' ');
        String[] tokens = value.split("[^a-z0-9]+");
        for (String token : tokens) {
            if (token.isEmpty()) continue;
            if (token.equals("ad")
                    || token.equals("ads")
                    || token.equals("advert")
                    || token.equals("advertisement")
                    || token.equals("commercial")
                    || token.equals("bumper")
                    || token.equals("insert")
                    || token.equals("insertion")
                    || token.equals("interstitial")
                    || token.equals("promo")
                    || token.equals("preroll")
                    || token.equals("midroll")
                    || token.equals("postroll")
                    || token.equals("scte35")
                    || token.equals("stitched")
                    || token.equals("vast")
                    || token.equals("vmap")) return true;
        }
        return false;
    }

    private static boolean isExplicitAdRecord(Record record) {
        if (!record.segment) return false;
        if (hasExplicitAdTag(record.tags)) return true;
        return isLikelyAdSegmentUri(record.line);
    }

    private static boolean hasExplicitAdTag(List<String> tags) {
        if (tags == null || tags.isEmpty()) return false;
        for (String tag : tags) {
            if (TextUtils.isEmpty(tag)) continue;
            if (isExplicitAdTag(tag)) return true;
        }
        return false;
    }

    private static boolean hasAdSignalTag(List<String> tags) {
        if (tags == null || tags.isEmpty()) return false;
        for (String tag : tags) {
            if (TextUtils.isEmpty(tag)) continue;
            if (isAdSignalTag(tag)) return true;
        }
        return false;
    }

    private static boolean isExplicitAdTag(String tag) {
        String lower = tag.toLowerCase(Locale.US);
        if (lower.startsWith("#ext-x-cue:") || lower.startsWith("#ext-x-cue-out-cont")) return true;
        if (lower.startsWith("#ext-oatcls-scte35")
                || lower.startsWith("#ext-x-scte35")
                || lower.startsWith("#ext-x-splicepoint-scte35")
                || lower.startsWith("#ext-x-asset")) return true;
        return containsAdKeyword(lower);
    }

    private static boolean isAdSignalTag(String tag) {
        String lower = tag.toLowerCase(Locale.US);
        return lower.startsWith("#ext-x-discontinuity")
                || lower.startsWith("#ext-x-program-date-time")
                || lower.startsWith("#ext-x-map:")
                || lower.startsWith("#ext-x-byterange");
    }

    private static boolean hasTagPrefix(List<String> tags, String prefix) {
        if (tags == null || tags.isEmpty()) return false;
        for (String tag : tags) {
            if (tag != null && tag.startsWith(prefix)) return true;
        }
        return false;
    }

    private static boolean isLikelyAdSegmentUri(String uri) {
        if (TextUtils.isEmpty(uri)) return false;
        String lower = uri.toLowerCase(Locale.US).trim();
        int fragment = lower.indexOf('#');
        if (fragment >= 0) lower = lower.substring(0, fragment);
        if (!containsAdKeyword(lower) && !containsAdQueryKey(lower)) return false;
        return isLikelySegmentResource(lower);
    }

    private static boolean containsAdQueryKey(String uri) {
        int query = uri.indexOf('?');
        if (query < 0 || query >= uri.length() - 1) return false;
        String[] parts = uri.substring(query + 1).split("&");
        for (String part : parts) {
            int index = part.indexOf('=');
            String key = index >= 0 ? part.substring(0, index) : part;
            if (containsAdKeyword(key)) return true;
        }
        return false;
    }

    private static boolean isLikelySegmentResource(String uri) {
        int query = uri.indexOf('?');
        String path = query >= 0 ? uri.substring(0, query) : uri;
        return path.endsWith(".ts")
                || path.endsWith(".m4s")
                || path.endsWith(".mp4")
                || path.endsWith(".cmfa")
                || path.endsWith(".cmfv")
                || path.endsWith(".aac")
                || path.endsWith(".mp3");
    }

    private static double parseDouble(String value) {
        try {
            return Double.parseDouble(value.trim());
        } catch (Throwable ignored) {
            return 0;
        }
    }

    private static String build(List<Record> records, int capacity) {
        StringBuilder sb = new StringBuilder(capacity);
        for (Record record : records) {
            if (!record.segment) sb.append(record.line).append('\n');
            else {
                for (String tag : record.tags) sb.append(tag).append('\n');
                sb.append(record.line).append('\n');
            }
        }
        if (sb.length() > 0) sb.setLength(sb.length() - 1);
        return sb.toString();
    }

    private static String resolveHost(String baseUrl, String uri) {
        try {
            String resolved = resolveUri(baseUrl, uri);
            String host = Uri.parse(resolved).getHost();
            return host == null ? "" : host.toLowerCase(Locale.US);
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static class Record {

        private final boolean segment;
        private final String line;
        private final String host;
        private final List<String> tags;
        private final double duration;

        private Record(boolean segment, String line, String host, List<String> tags, double duration) {
            this.segment = segment;
            this.line = line;
            this.host = host;
            this.tags = tags;
            this.duration = duration;
        }

        public static Record plain(String line) {
            return new Record(false, line, "", null, 0);
        }

        public static Record segment(List<String> tags, String line, String host, double duration) {
            return new Record(true, line, host, tags, duration);
        }
    }
}
