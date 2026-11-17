package com.fongmi.android.tv.utils;

import android.util.Log;

import com.fongmi.android.tv.BuildConfig;
import com.github.catvod.utils.UriUtil;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class M3u8AdFilter {

    private static final String TAG = "M3u8AdFilter";
    private static final int SUBTITLE_WHITELIST_MAX = 512;
    private static final int MAX_CONTIGUOUS_AD_SEGMENTS = 240;
    private static final int MAX_UNBOUNDED_CUE_AD_SEGMENTS = 20;
    private static final int MAX_LOG_CONTENT_CHARS = 8000;
    private static final java.util.regex.Pattern ISO_DATE_TIME = java.util.regex.Pattern.compile(
            "^(\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2})(?:\\.(\\d{1,9}))?(Z|[+-]\\d{2}:?\\d{2})$");
    private static final int URI_AD_LIKE = 1;
    private static final int URI_STRONG_AD = 1 << 1;
    // Frame-verified on 2026-09-26; see m3u8/research/README.md. Match these
    // specific repeated insertion profiles across sources, not arbitrary durations.
    private static final Set<String> CASINO_PROFILES = new HashSet<>(java.util.Arrays.asList(
            "4867,3333,5600,2867,2967,", "6960,4000,2560,3840,"));

    private static final Set<String> SUBTITLE_PLAYLIST_WHITELIST = Collections.newSetFromMap(new LinkedHashMap<String, Boolean>(SUBTITLE_WHITELIST_MAX, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
            return size() > SUBTITLE_WHITELIST_MAX;
        }
    });

    private static final Set<String> AD_SIGNAL_KEYWORDS = new HashSet<String>() {{
        add("ad");
        add("ads");
        add("adv");
        add("adid");
        add("adpod");
        add("adpods");
        add("adbreak");
        add("admarker");
        add("adserver");
        add("advert");
        add("adverts");
        add("advertise");
        add("advertiser");
        add("advertisement");
        add("advertising");
        add("vast");
        add("vmap");
        add("ima");
        add("imads");
        add("dfp");
        add("doubleclick");
        add("googlesyndication");
        add("googleads");
        add("gampad");
        add("pubads");
        add("adsystem");
        add("adsystems");
        add("adservice");
        add("adservices");
        add("videoad");
        add("videoads");
        add("videoadd");
        add("playad");
        add("commercial");
        add("commercials");
        add("bumper");
        add("insert");
        add("insertion");
        add("interstitial");
        add("sponsor");
        add("sponsored");
        add("promo");
        add("promos");
        add("promotion");
        add("preroll");
        add("pre-roll");
        add("pre_roll");
        add("midroll");
        add("mid-roll");
        add("mid_roll");
        add("postroll");
        add("post-roll");
        add("post_roll");
        add("scte");
        add("scte35");
        add("stitched");
        add("stitched-ad");
        add("stitched_ad");
        add("server-side-ad");
        add("server_side_ad");
        add("ssai");
        add("csai");
        add("spotx");
        add("freewheel");
        add("fw");
        add("innovid");
        add("aniview");
        add("vidazoo");
        add("yieldmo");
        add("jwplayerad");
        add("gam");
        add("companion");
        add("tracking");
        add("beacon");
        add("measurement");
        add("czad");
        add("guanggao");
        add("adjump");
        add("adslice");
        add("slicead");
        add("popad");
        add("popads");
        add("splashad");
        add("casino");
        add("gambling");
        add("kudad");
        add("kudianad");
        add("chabo");
        add("chaboad");
        add("kaipian");
        add("kaipianad");
        add("piantou");
        add("piantouad");
        add("pianwei");
        add("pianweiad");
        add("paomadeng");
        add("macau");
        add("pujing");
        add("xinpujing");
        add("bet365");
    }};

    private static final Set<String> STRONG_AD_KEYWORDS = new HashSet<>(java.util.Arrays.asList(
            "ad", "ads", "adv", "adid", "adpod", "adpods", "adbreak", "admarker",
            "adserver", "advert", "adverts", "advertise", "advertiser", "advertisement",
            "advertising", "vast", "vmap", "imads", "doubleclick", "googlesyndication",
            "googleads", "gampad", "pubads", "adsystem", "adsystems", "adservice",
            "adservices", "videoad", "videoads", "videoadd", "playad",
            "preroll", "midroll", "postroll", "scte35", "ssai", "csai",
            "czad", "guanggao", "adjump", "adslice", "slicead", "popad",
            "popads", "splashad", "kudad", "kudianad", "chaboad",
            "kaipianad", "piantouad", "pianweiad"));

    private static final Set<String> WEAK_AD_KEYWORDS = new HashSet<>(AD_SIGNAL_KEYWORDS);

    static {
        WEAK_AD_KEYWORDS.removeAll(STRONG_AD_KEYWORDS);
    }

    private static final Set<String> AD_QUERY_KEYS = new HashSet<String>() {{
        add("ad");
        add("ads");
        add("adv");
        add("adid");
        add("ad_id");
        add("ad-id");
        add("adunit");
        add("ad_unit");
        add("ad-unit");
        add("adtype");
        add("ad_type");
        add("ad-type");
        add("adbreak");
        add("ad_break");
        add("ad-break");
        add("adpod");
        add("ad_pod");
        add("ad-pod");
        add("adtagurl");
        add("ad_tag_url");
        add("ad-tag-url");
        add("adsystem");
        add("ad_system");
        add("ad-system");
        add("advertising");
        add("commercial");
        add("commercial_id");
        add("commercial-id");
        add("scte");
        add("scte35");
        add("ssai");
        add("csai");
        add("vmap");
        add("vast");
        add("czad");
        add("guanggao");
        add("adjump");
    }};

    private static final Set<String> AD_PATH_PARTS = new HashSet<String>() {{
        add("ad");
        add("ads");
        add("adv");
        add("advert");
        add("advertising");
        add("commercial");
        add("commercials");
        add("promo");
        add("promos");
        add("preroll");
        add("midroll");
        add("postroll");
        add("adbreak");
        add("adpod");
        add("ssai");
        add("csai");
        add("stitched");
        add("scte");
        add("scte35");
        add("vast");
        add("vmap");
        add("doubleclick");
        add("gampad");
        add("pubads");
        add("freewheel");
        add("spotx");
        add("ima");
        add("czad");
        add("guanggao");
        add("imads");
        add("adjump");
        add("adslice");
        add("slicead");
        add("popad");
        add("popads");
        add("splashad");
        add("casino");
        add("gambling");
        add("kudad");
        add("kudianad");
        add("chabo");
        add("chaboad");
        add("kaipian");
        add("kaipianad");
        add("piantou");
        add("piantouad");
        add("pianwei");
        add("pianweiad");
        add("paomadeng");
        add("macau");
        add("pujing");
        add("xinpujing");
        add("bet365");
    }};

    private M3u8AdFilter() {
    }

    public static boolean isLikelyM3u8(String contentType, String url) {
        String ct = contentType == null ? "" : contentType.toLowerCase(Locale.US);
        String lowerUrl = url == null ? "" : url.toLowerCase(Locale.US);
        return ct.contains("mpegurl")
                || ct.contains("vnd.apple.mpegurl")
                || ct.contains("x-mpegurl")
                || lowerUrl.contains(".m3u8")
                || lowerUrl.contains(".m3u");
    }

    public static byte[] filterMinorHost(byte[] bytes, String baseUrl) {
        if (bytes == null || bytes.length == 0) return bytes;

        String content = new String(bytes, StandardCharsets.UTF_8);
        int headerIndex = findM3uHeaderIndex(content);
        if (headerIndex < 0) return bytes;
        if (headerIndex > 0) content = content.substring(headerIndex);

        if (isLowLatencyHls(content)) return bytes;
        if (isSubtitleWhitelisted(baseUrl)) return toBytesOrOriginal(content, bytes);

        registerSubtitlePlaylists(content, baseUrl);
        // Media sequences in EVENT and live playlists must retain their original numbering.
        // Master playlists have no media segments and can still be filtered below.
        if (content.contains("#EXTINF") && !isVodPlaylist(content)) return bytes;
        content = filterAdVariantPlaylists(content, baseUrl);

        if (!content.contains("#EXTINF") && !content.contains("#EXT-X-PART")) {
            return toBytesOrOriginal(content, bytes);
        }

        List<Record> records = parseRecords(content, baseUrl);
        if (isSubtitlePlaylist(content, records)) return toBytesOrOriginal(content, bytes);

        boolean vod = isVodPlaylist(content);
        int originalSegmentCount = countSegments(records);
        List<Record> originalRecords = records;
        Set<String> repeatedSegments = collectRepeatedSegments(records);
        List<Record> timed = filterTimedAdRanges(originalRecords);
        records = filterAdTagSegments(originalRecords, baseUrl, vod);
        if (timed != originalRecords) {
            // Cue durations describe the original sequence, even when DATERANGE marks the same break.
            Set<Record> outsideTimedBreaks = new HashSet<>(timed);
            List<Record> kept = new ArrayList<>(records);
            kept.removeIf(record -> !outsideTimedBreaks.contains(record));
            records = kept;
        }
        records = filterRepeatedDiscontinuityPods(records, baseUrl, vod);
        if (vod) records = filterShortAdClusters(records);
        Set<String> confirmedRepeatedAds = collectRemovedRepeatedMedia(originalRecords, records, repeatedSegments);
        records = filterRepeatedSegments(records, confirmedRepeatedAds);

        records = filterMinorHostSegments(records);
        records = restoreRetainedPlaybackState(originalRecords, records);
        String filtered = build(records, content.length());
        int filteredSegmentCount = countSegmentsFromString(filtered);
        // Never hand Exo a playlist that the filter has emptied. A false-positive ad rule
        // should leave the original stream playable rather than turn into a playback error.
        if (!startsWithM3uHeader(filtered) || filteredSegmentCount == 0) return toBytesOrOriginal(content, bytes);
        if (shouldKeepOriginalForHlsIntegrity(content, originalSegmentCount, filteredSegmentCount)) return toBytesOrOriginal(content, bytes);

        logFilterResult(baseUrl, content, filtered);
        return toBytesOrOriginal(filtered, bytes);
    }

    private static void logFilterResult(String baseUrl, String original, String filtered) {
        if (!BuildConfig.DEBUG || original == null || filtered == null || original.equals(filtered)) return;

        try {
            int before = countSegmentsFromString(original);
            int after = countSegmentsFromString(filtered);
            Log.d(TAG, "url=" + baseUrl + ", before=" + before + ", after=" + after + ", removed=" + (before - after)
                    + ", bytes=" + original.length() + "->" + filtered.length());
            Log.d(TAG, "original=\n" + truncateLogContent(original));
            Log.d(TAG, "filtered=\n" + truncateLogContent(filtered));
        } catch (Throwable ignored) {
        }
    }

    private static String truncateLogContent(String content) {
        if (content == null || content.length() <= MAX_LOG_CONTENT_CHARS) return content;
        return content.substring(0, MAX_LOG_CONTENT_CHARS)
                + "\n... truncated, totalChars=" + content.length();
    }

    private static boolean startsWithM3uHeader(String content) {
        return findM3uHeaderIndex(content) == 0;
    }

    private static int findM3uHeaderIndex(String content) {
        if (content == null || content.isEmpty()) return -1;
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
            String line = trimLineEnd(raw).trim();
            if (!line.startsWith("#EXT-X-MEDIA:")) continue;

            String type = parseAttributeString(line, "TYPE");
            if (!"SUBTITLES".equalsIgnoreCase(type) && !"CLOSED-CAPTIONS".equalsIgnoreCase(type)) continue;

            String uri = parseAttributeString(line, "URI");
            addSubtitleWhitelist(baseUrl, uri);
        }
    }

    private static void addSubtitleWhitelist(String baseUrl, String uri) {
        if (isEmpty(uri)) return;
        String resolved = resolveUri(baseUrl, uri);
        if (isEmpty(resolved)) return;
        synchronized (SUBTITLE_PLAYLIST_WHITELIST) {
            SUBTITLE_PLAYLIST_WHITELIST.add(normalizeForMatch(resolved));
        }
    }

    private static boolean isSubtitleWhitelisted(String url) {
        if (isEmpty(url)) return false;
        String full = normalizeForMatch(url);
        synchronized (SUBTITLE_PLAYLIST_WHITELIST) {
            return SUBTITLE_PLAYLIST_WHITELIST.contains(full);
        }
    }

    /** Clears per-playback subtitle URLs after the player is released. */
    public static void clearSubtitlePlaylistWhitelist() {
        synchronized (SUBTITLE_PLAYLIST_WHITELIST) {
            SUBTITLE_PLAYLIST_WHITELIST.clear();
        }
    }

    private static String normalizeForMatch(String url) {
        // Query parameters can select entirely different tracks at one endpoint.
        // Paths and query values are case-sensitive; only fragments are irrelevant.
        String value = url.trim();
        int h = value.indexOf('#');
        return h >= 0 ? value.substring(0, h) : value;
    }

    private static String resolveUri(String baseUrl, String uri) {
        try {
            return UriUtil.resolve(baseUrl, uri);
        } catch (Throwable ignored) {
            try {
                return new URI(baseUrl).resolve(uri).toString();
            } catch (Exception invalidUri) {
                return uri;
            }
        }
    }

    private static boolean isSubtitlePlaylist(String content, List<Record> records) {
        String lowerContent = content.toLowerCase(Locale.US);

        if (lowerContent.startsWith("webvtt") || lowerContent.contains("\nwebvtt")) return true;

        int subtitleUriCount = 0;
        int segmentCount = 0;

        for (Record record : records) {
            if (record.segment) segmentCount++;
            if (record.segment && isSubtitleUri(record.line)) subtitleUriCount++;
            if (record.tags == null) continue;
            for (String tag : record.tags) {
                if (isSubtitleMapTag(tag)) return true;
            }
        }

        return segmentCount > 0 && subtitleUriCount == segmentCount;
    }

    private static boolean isSubtitleMapTag(String tag) {
        if (!tag.startsWith("#EXT-X-MAP:")) return false;
        String uri = parseAttributeString(tag, "URI");
        return isSubtitleUri(uri);
    }

    private static boolean isSubtitleUri(String uri) {
        if (isEmpty(uri)) return false;
        String lower = stripQueryAndFragment(uri.toLowerCase(Locale.US));
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
                || lower.endsWith(".stl")
                || lower.endsWith(".sbv")
                || lower.endsWith(".xml");
    }

    static String filterAdVariantPlaylists(String content, String baseUrl) {
        if (!content.contains("#EXT-X-STREAM-INF")
                && !content.contains("#EXT-X-I-FRAME-STREAM-INF")
                && !content.contains("#EXT-X-MEDIA")) return content;

        String[] lines = content.split("\n", -1);
        boolean[] remove = new boolean[lines.length];
        Map<String, Integer> mediaGroupCounts = countMediaGroups(lines);
        int variants = 0;
        int adVariants = 0;
        int regularVariants = 0;
        int adRegularVariants = 0;

        for (int i = 0; i < lines.length; i++) {
            String line = trimLineEnd(lines[i]).trim();

            if (line.startsWith("#EXT-X-I-FRAME-STREAM-INF")) {
                variants++;
                String uri = parseAttributeString(line, "URI");
                if (isLikelyAdPlaylistUri(uri, baseUrl)) {
                    remove[i] = true;
                    adVariants++;
                }
                continue;
            }

            if (line.startsWith("#EXT-X-MEDIA:")) {
                String type = parseAttributeString(line, "TYPE");
                String uri = parseAttributeString(line, "URI");
                String name = parseAttributeString(line, "NAME");

                if ("SUBTITLES".equalsIgnoreCase(type) || "CLOSED-CAPTIONS".equalsIgnoreCase(type)) {
                    continue;
                }
                if ("AUDIO".equalsIgnoreCase(type) && parseAttributeString(line, "CHARACTERISTICS")
                        .contains("public.accessibility.describes-video")) continue;

                String groupKey = mediaGroupKey(type, parseAttributeString(line, "GROUP-ID"));
                boolean hasAlternative = mediaGroupCounts.getOrDefault(groupKey, 0) > 1;
                // AD is also the standard abbreviation for audio description.
                String adName = "AUDIO".equalsIgnoreCase(type) ? name.replaceAll("(?i)\\bad\\b", "") : name;
                if (hasAlternative && !isEmpty(uri) && (isLikelyAdPlaylistUri(uri, baseUrl) || containsAdKeyword(adName))) {
                    remove[i] = true;
                    mediaGroupCounts.put(groupKey, mediaGroupCounts.get(groupKey) - 1);
                }
                continue;
            }

            if (!line.startsWith("#EXT-X-STREAM-INF")) continue;

            int uriIndex = findNextUriLine(lines, i + 1);
            if (uriIndex < 0) continue;

            variants++;
            regularVariants++;
            String uri = trimLineEnd(lines[uriIndex]).trim();

            if (isLikelyAdPlaylistUri(uri, baseUrl)) {
                remove[i] = true;
                remove[uriIndex] = true;
                adVariants++;
                adRegularVariants++;
            }
        }

        boolean changed = false;
        for (boolean value : remove) changed |= value;
        if (!changed) return content;
        if (variants > 0 && adVariants >= variants) return content;
        if (regularVariants > 0 && adRegularVariants >= regularVariants) return content;

        StringBuilder sb = new StringBuilder(content.length());
        for (int i = 0; i < lines.length; i++) {
            if (remove[i]) continue;
            sb.append(lines[i]);
            if (i < lines.length - 1) sb.append('\n');
        }
        return sb.toString();
    }

    private static Map<String, Integer> countMediaGroups(String[] lines) {
        Map<String, Integer> counts = new HashMap<>();
        for (String raw : lines) {
            String line = trimLineEnd(raw).trim();
            if (!line.startsWith("#EXT-X-MEDIA:")) continue;
            String type = parseAttributeString(line, "TYPE");
            String groupId = parseAttributeString(line, "GROUP-ID");
            if (isEmpty(type) || isEmpty(groupId)) continue;
            String key = mediaGroupKey(type, groupId);
            counts.put(key, counts.getOrDefault(key, 0) + 1);
        }
        return counts;
    }

    private static String mediaGroupKey(String type, String groupId) {
        return type.toLowerCase(Locale.US) + '\n' + groupId;
    }

    private static int findNextUriLine(String[] lines, int start) {
        for (int i = start; i < lines.length; i++) {
            String line = trimLineEnd(lines[i]).trim();
            if (line.isEmpty()) continue;
            if (line.startsWith("#")) return -1;
            return i;
        }
        return -1;
    }

    private static String trimLineEnd(String raw) {
        if (raw == null) return "";
        return raw.endsWith("\r") ? raw.substring(0, raw.length() - 1) : raw;
    }

    private static boolean isLikelyAdPlaylistUri(String uri, String baseUrl) {
        if (isEmpty(uri)) return false;
        String resolved = resolveUri(baseUrl, uri).toLowerCase(Locale.US).trim();
        int hash = resolved.indexOf('#');
        if (hash >= 0) resolved = resolved.substring(0, hash);
        // STREAM-INF/MEDIA already establish that this URI is a playlist.
        // Extensionless endpoints still require a strong, rather than generic promo, signal.
        return (isLikelyPlaylistResource(resolved) && isAdLikeUri(resolved))
                || isStrongAdLikeUri(resolved);
    }

    private static boolean isLikelyPlaylistResource(String uri) {
        String path = stripQueryAndFragment(uri);
        return path.endsWith(".m3u8") || path.endsWith(".m3u");
    }

    private static List<Record> filterMinorHostSegments(List<Record> records) {
        Map<String, Integer> hostCount = new HashMap<>();
        int segmentCount = 0;

        for (Record record : records) {
            if (!record.segment) continue;
            segmentCount++;
            String h = record.host == null ? "" : record.host;
            hostCount.put(h, hostCount.getOrDefault(h, 0) + 1);
        }

        if (segmentCount < 2 || hostCount.size() < 2) return records;

        String majorHost = null;
        int majorCount = -1;

        for (Map.Entry<String, Integer> entry : hostCount.entrySet()) {
            if (entry.getValue() > majorCount) {
                majorHost = entry.getKey();
                majorCount = entry.getValue();
            }
        }

        if (majorHost == null) return records;

        boolean hasStableMajorHost = (majorCount * 10) >= (segmentCount * 5);
        if (!hasStableMajorHost) return records;
        List<Record> kept = new ArrayList<>(records.size());
        int removed = 0;

        for (int index = 0; index < records.size(); index++) {
            Record record = records.get(index);
            if (!record.segment) {
                kept.add(record);
                continue;
            }

            String h = record.host == null ? "" : record.host;
            boolean sameAsMajor = isEmpty(h) || majorHost.equals(h);
            boolean explicitAd = isExplicitAdRecord(record);
            // CDN balancing does not corroborate a random AD1... filename.
            // Explicit ad tags/paths still win; structural number rules run earlier.
            boolean minorAdHost = !sameAsMajor && record.adLikeUri && !record.weakAdNumberUri;

            if (explicitAd || minorAdHost) {
                removed++;
                continue;
            }

            kept.add(record);
        }

        if (removed <= 0) return records;
        if (removed >= segmentCount) return records;

        return kept;
    }

    static int countSegmentsFromString(String content) {
        if (content == null || content.isEmpty()) return 0;
        int count = 0;
        String[] lines = content.split("\n", -1);
        boolean waitUri = false;

        for (String raw : lines) {
            String line = trimLineEnd(raw).trim();
            if (line.startsWith("#EXT-X-PART:")) {
                if (!isEmpty(parseAttributeString(line, "URI"))) count++;
                continue;
            }
            if (line.startsWith("#EXTINF")) {
                waitUri = true;
                continue;
            }
            if (waitUri && !line.isEmpty() && !line.startsWith("#")) {
                count++;
                waitUri = false;
            }
        }

        return count;
    }

    private static List<Record> filterTimedAdRanges(List<Record> records) {
        Map<String, TimedAdRange> byId = new HashMap<>();
        for (Record record : records) {
            if (record.segment || !record.line.startsWith("#EXT-X-DATERANGE:")) continue;
            String id = parseAttributeString(record.line, "ID");
            if (!isEmpty(id)) byId.computeIfAbsent(id, ignored -> new TimedAdRange()).update(record.line);
        }
        List<TimedAdRange> ranges = new ArrayList<>();
        for (TimedAdRange range : byId.values()) if (range.isBoundedAd()) ranges.add(range);
        if (ranges.isEmpty()) return records;

        List<Record> kept = new ArrayList<>(records.size());
        double time = Double.NaN;
        for (Record record : records) {
            if (!record.segment) {
                if (record.line.equals("#EXT-X-DISCONTINUITY")) time = Double.NaN;
                if (record.line.startsWith("#EXT-X-PROGRAM-DATE-TIME:")) time = parseProgramTime(record.line);
                kept.add(record);
                continue;
            }
            // Never extrapolate a wall-clock anchor through a timestamp discontinuity.
            if (hasTagPrefix(record.tags, "#EXT-X-DISCONTINUITY")) time = Double.NaN;
            String anchor = lastTag(record.tags, "#EXT-X-PROGRAM-DATE-TIME:");
            if (!isEmpty(anchor)) time = parseProgramTime(anchor);
            double end = time + record.duration * 1000;
            boolean ad = false;
            if (!Double.isNaN(time) && record.duration > 0) {
                for (TimedAdRange range : ranges) {
                    // A partly overlapping segment can also contain primary content.
                    if (time >= range.start && end <= range.endTime()) { ad = true; break; }
                }
            }
            if (!ad) kept.add(record);
            time = record.duration > 0 ? end : Double.NaN;
        }
        return countSegments(kept) == 0 ? records : kept;
    }

    private static double parseProgramTime(String line) {
        long value = parseDateTime(line.substring(line.indexOf(':') + 1));
        return value == Long.MIN_VALUE ? Double.NaN : value;
    }

    private static long parseDateTime(String value) {
        java.util.regex.Matcher match = ISO_DATE_TIME.matcher(value);
        if (!match.matches()) return Long.MIN_VALUE;
        String fraction = match.group(2) == null ? "000" : (match.group(2) + "000").substring(0, 3);
        String zone = "Z".equals(match.group(3)) ? "+0000" : match.group(3).replace(":", "");
        String normalized = match.group(1) + "." + fraction + zone;
        java.text.SimpleDateFormat format = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US);
        format.setLenient(false);
        java.text.ParsePosition position = new java.text.ParsePosition(0);
        java.util.Date date = format.parse(normalized, position);
        return date != null && position.getIndex() == normalized.length() ? date.getTime() : Long.MIN_VALUE;
    }

    private static final class TimedAdRange {
        long start = Long.MIN_VALUE;
        long end = Long.MIN_VALUE;
        double duration;
        boolean ad;
        boolean interstitial;
        boolean invalid;

        void update(String line) {
            String clazz = parseAttributeString(line, "CLASS");
            interstitial |= clazz.equalsIgnoreCase("com.apple.hls.interstitial");
            ad |= containsStrongAdKeyword(clazz)
                    || containsStrongAdKeyword(parseAttributeString(line, "X-CONTENT-TYPE"))
                    || !isEmpty(parseAttributeString(line, "SCTE35-OUT"))
                    || !isEmpty(parseAttributeString(line, "X-AD-ID"));
            String startText = parseAttributeString(line, "START-DATE");
            if (!isEmpty(startText)) {
                long parsed = parseDateTime(startText);
                invalid |= parsed == Long.MIN_VALUE || (start != Long.MIN_VALUE && start != parsed);
                start = parsed;
            }
            String endText = parseAttributeString(line, "END-DATE");
            if (!isEmpty(endText)) {
                long parsed = parseDateTime(endText);
                invalid |= parsed == Long.MIN_VALUE || (end != Long.MIN_VALUE && end != parsed);
                end = parsed;
            }
            String durationText = parseAttributeString(line, "DURATION");
            if (!isEmpty(durationText)) {
                double parsed = parseDouble(durationText) * 1000;
                invalid |= !Double.isFinite(parsed) || parsed <= 0 || (duration > 0 && duration != parsed);
                duration = parsed;
            }
        }

        double endTime() { return end != Long.MIN_VALUE ? end : start + duration; }

        boolean isBoundedAd() {
            return ad && !interstitial && !invalid && start != Long.MIN_VALUE
                    && (end != Long.MIN_VALUE || duration > 0) && endTime() > start
                    && (end == Long.MIN_VALUE || duration <= 0 || Math.abs(end - start - duration) < 1);
        }
    }

    private static List<Record> filterAdTagSegments(List<Record> records, String baseUrl, boolean vod) {
        List<Record> kept = new ArrayList<>(records.size());
        boolean allowWeakDiscontinuitySignal = vod && shouldUseWeakDiscontinuitySignal(records);

        boolean cueAdOpen = false;
        boolean cueAdHasClosingTag = false;
        double cueAdSeconds = 0;
        int cueAdSegments = 0;

        boolean pendingAdSignal = false;
        int pendingAdSignalLines = 0;

        for (int recordIndex = 0; recordIndex < records.size(); recordIndex++) {
            Record record = records.get(recordIndex);
            if (!record.segment) {
                if (isCueOutTag(record.line)) {
                    cueAdHasClosingTag = hasCueInAhead(records, recordIndex + 1);
                    cueAdSeconds = parseCueOutDuration(record.line);
                    cueAdOpen = cueAdSeconds >= 0;
                    cueAdSeconds = Math.max(0, cueAdSeconds);
                    cueAdSegments = 0;
                    continue;
                }

                if (isCueInTag(record.line)) {
                    cueAdOpen = false;
                    cueAdHasClosingTag = false;
                    cueAdSeconds = 0;
                    cueAdSegments = 0;
                    pendingAdSignal = false;
                    pendingAdSignalLines = 0;
                    continue;
                }

                if (isAdDateRangeTag(record.line)) {
                    // DATERANGE describes a wall-clock interval. Its position in
                    // the playlist does not identify the following media segments.
                    continue;
                }

                if (isAdSignalLine(record.line)) {
                    pendingAdSignal = true;
                    pendingAdSignalLines = 0;
                    continue;
                }

                if (isStandaloneAdResourceTag(record.line, baseUrl)) continue;
                if (isPlainAdResourceUri(record.line, baseUrl)) continue;

                if (record.line.startsWith("#EXT-X-CUE-")) continue;
                if (record.line.startsWith("#EXT-OATCLS-SCTE35")) continue;
                if (record.line.startsWith("#EXT-X-SCTE35")) continue;
                if (record.line.startsWith("#EXT-X-SPLICEPOINT-SCTE35")) continue;
                if (record.line.startsWith("#EXT-X-ASSET:")) continue;

                kept.add(record);

                if (pendingAdSignal) {
                    pendingAdSignalLines++;
                    if (pendingAdSignalLines > 5) {
                        pendingAdSignal = false;
                        pendingAdSignalLines = 0;
                    }
                }

                continue;
            }

            boolean hasDiscontinuity = hasTagPrefix(record.tags, "#EXT-X-DISCONTINUITY");
            boolean explicitAd = isExplicitAdRecord(record)
                    || (allowWeakDiscontinuitySignal && hasDiscontinuity && record.weakAdNumberUri);
            boolean adByPendingSignal = pendingAdSignal && (explicitAd || record.adLikeUri || hasAdSignalTag(record.tags));

            // Account for every segment in a declared break before URI/pod rules
            // can skip it. Otherwise the remaining duration spills into content.
            if (cueAdOpen && shouldEndCue(cueAdSeconds, cueAdSegments, cueAdHasClosingTag)) {
                cueAdOpen = false;
                cueAdHasClosingTag = false;
            }
            if (cueAdOpen) {
                if (cueAdOpen) {
                    cueAdSegments++;
                    if (cueAdSeconds > 0) {
                        cueAdSeconds = nextRemaining(cueAdSeconds, record.duration);
                        if (cueAdSeconds <= 0) cueAdOpen = false;
                    }
                }
                pendingAdSignal = false;
                continue;
            }

            // A discontinuity marks a media/timestamp change, not an ad interval.
            // URI evidence applies only to this segment; cues delimit full breaks.
            if (explicitAd || adByPendingSignal) {
                pendingAdSignal = false;
                pendingAdSignalLines = 0;
                continue;
            }

            kept.add(record);

            if (pendingAdSignal) {
                pendingAdSignalLines++;
                if (pendingAdSignalLines > 2) {
                    pendingAdSignal = false;
                    pendingAdSignalLines = 0;
                }
            }
        }

        if (countSegments(kept) <= 0) return records;
        return kept;
    }

    private static boolean hasCueInAhead(List<Record> records, int start) {
        int segments = 0;
        for (int index = start; index < records.size() && segments <= MAX_CONTIGUOUS_AD_SEGMENTS; index++) {
            Record record = records.get(index);
            if (record.segment) segments++;
            else if (isCueInTag(record.line)) return true;
            else if (isCueOutTag(record.line)) return false;
        }
        return false;
    }

    private static boolean shouldUseWeakDiscontinuitySignal(List<Record> records) {
        int segments = 0;
        int discontinuities = 0;
        for (Record record : records) {
            if (!record.segment) continue;
            segments++;
            if (hasTagPrefix(record.tags, "#EXT-X-DISCONTINUITY")) discontinuities++;
        }
        return discontinuities > 0 && discontinuities * 20 <= segments;
    }

    private static Set<String> collectRepeatedSegments(List<Record> records) {
        Map<String, Integer> counts = new HashMap<>();
        for (Record record : records) {
            if (!record.segment) continue;
            String identity = exactMediaIdentity(record);
            if (!isEmpty(identity)) counts.put(identity, counts.getOrDefault(identity, 0) + 1);
        }

        Set<String> repeated = new HashSet<>();
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            if (entry.getValue() > 1) repeated.add(entry.getKey());
        }
        return repeated;
    }

    private static Set<String> collectRemovedRepeatedMedia(List<Record> original, List<Record> kept, Set<String> repeated) {
        if (repeated.isEmpty() || original.size() == kept.size()) return Collections.emptySet();
        Set<Record> retained = new HashSet<>(kept);
        Set<String> confirmed = new HashSet<>();
        for (Record record : original) {
            if (!record.segment || retained.contains(record)) continue;
            String identity = exactMediaIdentity(record);
            if (!isEmpty(identity) && repeated.contains(identity)) confirmed.add(identity);
        }
        return confirmed;
    }

    // URI reuse alone also occurs for normal still frames and broadcast fillers.
    // Propagate only a decision already supported by a cue, resource signal or pod.
    private static List<Record> filterRepeatedSegments(List<Record> records, Set<String> repeatedSegments) {
        if (records == null || records.isEmpty() || repeatedSegments == null || repeatedSegments.isEmpty()) return records;

        List<Record> kept = new ArrayList<>(records.size());
        int segmentCount = 0;
        int removedSegments = 0;

        for (Record record : records) {
            if (!record.segment) {
                kept.add(record);
                continue;
            }

            segmentCount++;
            String identity = exactMediaIdentity(record);
            if (!isEmpty(identity) && repeatedSegments.contains(identity)) {
                removedSegments++;
                continue;
            }

            kept.add(record);
        }

        if (removedSegments <= 0 || removedSegments >= segmentCount) return records;
        return kept;
    }

    private static boolean isLowLatencyHls(String content) {
        if (isEmpty(content)) return false;
        return content.contains("#EXT-X-PART-INF:")
                || content.contains("#EXT-X-PART:")
                || content.contains("#EXT-X-SKIP:")
                || content.contains("#EXT-X-PRELOAD-HINT:")
                || content.contains("#EXT-X-SERVER-CONTROL:");
    }

    private static boolean isVodPlaylist(String content) {
        boolean vod = false;
        for (String line : content.split("\n")) {
            String tag = line.trim();
            // A completed EVENT is still the same sequence clients were refreshing.
            // ENDLIST must not suddenly renumber it by enabling segment removal.
            if (tag.equals("#EXT-X-PLAYLIST-TYPE:EVENT")) return false;
            if (tag.equals("#EXT-X-ENDLIST") || tag.equals("#EXT-X-PLAYLIST-TYPE:VOD")) vod = true;
        }
        return vod;
    }

    public static String identifyProvider(String url) {
        if (isEmpty(url)) return "unknown";
        String lower;
        try {
            String host = new URI(url).getHost();
            if (isEmpty(host)) return "unknown";
            lower = host.toLowerCase(Locale.US);
        } catch (Exception ignored) {
            return "unknown";
        }
        if (lower.contains("lzcdn") || lower.contains("cdnlz") || lower.contains("lzzy") || lower.contains("liangzi")) return "liangzi";
        if (lower.contains("ffzy") || lower.contains("feifan")) return "feifan";
        if (lower.contains("bfikun") || lower.contains("ikun")) return "ikun";
        if (lower.contains("suoni") || lower.contains("rstu") || lower.contains("snzy") || lower.contains("suonizy")) return "suoni";
        if (lower.contains("bfvvs") || lower.contains("hongniu") || lower.contains("hnzy")) return "hongniu";
        if (lower.contains("fengbao") || lower.contains("baofeng") || lower.contains("bfzy")
                || lower.contains("bfbfvip") || lower.contains("bfengbf")) return "baofeng";
        if (lower.contains("wolong") || lower.startsWith("cdn.wl")) return "wolong";
        if (lower.contains("jszy") || lower.contains("jisu") || lower.contains("muguacdn")) return "jisu";
        if (lower.contains("xinsj")) return "xinsj";
        if (lower.contains("kuaikan")) return "kuaikan";
        if (lower.contains("ukzy") || lower.contains("ukuapi")) return "uku";
        if (lower.contains("qihubf") || lower.contains("qhzy")) return "qihu";
        if (lower.contains("haiwaikan")) return "haiwaikan";
        if (lower.contains("ryplay")) return "ryplay";
        if (lower.contains("apiyhzy") || lower.contains("yinghua")) return "yinghua";
        if (lower.contains("czzy")) return "changzhang";
        if (lower.contains("changze")) return "changze";
        if (lower.contains("modu") || lower.contains("modujx")) return "modu";
        if (lower.contains("kudian") || lower.contains("kdzy") || lower.contains("kuapi")) return "kudian";
        return "unknown";
    }

    private static List<Record> filterRepeatedDiscontinuityPods(List<Record> records, String baseUrl, boolean vod) {
        if (records == null || records.isEmpty()) return records;

        String provider = identifyProvider(baseUrl);
        List<Pod> pods = new ArrayList<>();
        int podStart = -1;
        String activeKey = "";
        String podBeforeKey = "";
        for (int i = 0; i < records.size(); i++) {
            Record record = records.get(i);
            if (!record.segment) continue;
            String beforeKey = activeKey;
            String key = Pod.lastKey(record.tags);
            if (!isEmpty(key)) activeKey = key;
            // A preroll starts at the playlist boundary and need not carry its
            // own discontinuity. Include it in exact repeated-pod matching.
            if (podStart < 0) {
                podStart = i;
                podBeforeKey = beforeKey;
            } else if (hasTagPrefix(record.tags, "#EXT-X-DISCONTINUITY")) {
                // This segment's effective key is the preceding pod's after-key.
                addPod(pods, records, podStart, i, podBeforeKey, activeKey);
                podStart = i;
                podBeforeKey = beforeKey;
            }
        }
        if (podStart >= 0) addPod(pods, records, podStart, records.size(), podBeforeKey, activeKey);

        Map<String, Integer> durationCounts = new HashMap<>();
        Map<String, Integer> mediaCounts = new HashMap<>();
        Map<Pod, String> casinoProfiles = new HashMap<>();
        Map<String, Integer> casinoCounts = new HashMap<>();
        // These exact frame-verified duration profiles already require repeated
        // matches and long main-content neighbors; density is not a useful gate.
        boolean allowCasinoProfiles = vod;
        for (Pod pod : pods) {
            durationCounts.put(pod.durationFingerprint, durationCounts.getOrDefault(pod.durationFingerprint, 0) + 1);
            mediaCounts.put(pod.mediaFingerprint, mediaCounts.getOrDefault(pod.mediaFingerprint, 0) + 1);
            String profile = allowCasinoProfiles ? verifiedCasinoProfile(records, pod) : "";
            if (!profile.isEmpty()) {
                casinoProfiles.put(pod, profile);
                casinoCounts.put(profile, casinoCounts.getOrDefault(profile, 0) + 1);
            }
        }

        boolean[] remove = new boolean[records.size()];
        int removedSegments = 0;
        boolean repeatedVerifiedCasinoProfile = false;
        for (int count : casinoCounts.values()) {
            if (count >= 2) repeatedVerifiedCasinoProfile = true;
        }
        for (Pod pod : pods) {
            int matchingDurations = durationCounts.getOrDefault(pod.durationFingerprint, 0);
            int matchingMedia = mediaCounts.getOrDefault(pod.mediaFingerprint, 0);
            // A second verified creative may appear only once alongside a
            // repeated one. Both still need exact profiles and long neighbors.
            boolean isCasino = allowCasinoProfiles && casinoProfiles.containsKey(pod)
                    && (casinoCounts.getOrDefault(casinoProfiles.get(pod), 0) >= 2
                    || repeatedVerifiedCasinoProfile);
            boolean boundedNumericExcursion = vod && "liangzi".equals(provider)
                    && hasBoundedNumericExcursion(records, pod);
            int score = calculatePodScore(records, pods, pod, matchingDurations, matchingMedia, provider, vod);
            if (!isCasino && !boundedNumericExcursion && score < 6) continue;
            for (int i = pod.start; i < pod.end; i++) {
                if (records.get(i).segment) removedSegments++;
                remove[i] = true;
            }
        }

        int segmentCount = countSegments(records);
        if (removedSegments <= 0 || removedSegments >= segmentCount) return records;

        List<Record> kept = new ArrayList<>(records.size());
        for (int i = 0; i < records.size(); i++) if (!remove[i]) kept.add(records.get(i));
        return kept;
    }

    private static String verifiedCasinoProfile(List<Record> records, Pod pod) {
        StringBuilder fingerprint = new StringBuilder();
        for (int i = pod.start; i < pod.end; i++) {
            Record record = records.get(i);
            if (!record.segment) continue;
            if (isPartOnlyRecord(record)) return "";
            fingerprint.append(Math.round(record.duration * 1000)).append(',');
        }
        String profile = fingerprint.toString();
        if (!CASINO_PROFILES.contains(profile)) return "";
        if (!hasLongMainNeighborOrBoundary(records, pod.start - 1, -1)
                || !hasLongMainNeighborOrBoundary(records, pod.end, 1)) return "";
        return profile;
    }

    private static int calculatePodScore(List<Record> records, List<Pod> pods, Pod pod, int matchingDurations, int matchingMedia, String provider, boolean vod) {
        if (!vod) return 0;
        if ("hongniu".equals(provider) && !pod.strongAdSignal) return 0;

        int score = 0;

        boolean longNeighbors = hasLongMainNeighborOrBoundary(records, pod.start - 1, -1)
                && hasLongMainNeighborOrBoundary(records, pod.end, 1);
        boolean pathDisparity = hasPathDisparity(records, pod);
        boolean indexJump = hasIndexJump(records, pod);
        boolean repeatedDirectory = hasRepeatedPodDirectory(records, pods, pod);
        boolean repeatedMediaLocation = pod.segmentCount >= 2 && matchingMedia >= 2;
        boolean repeatedForeignDirectory = repeatedDirectory && pathDisparity;
        boolean providerMediaSignature = hasProviderMediaSignature(records, pod, provider);
        if (pod.strongAdSignal) score += 5;
        // Community rules for qihubf / 快看 / U酷 / iKun consistently mark a
        // short DISCONTINUITY pod that switches the key to METHOD=NONE, then
        // returns to encrypted media. Compare key *methods* rather than literal
        // key lines because the content key URI may legitimately rotate.
        if (pod.clearKeyExcursionSignal) score += 6;
        else if (pod.keySwitchSignal) score += 3;
        // Provider-specific URI fingerprints are useful corroboration, but not
        // sufficient on their own. They still need structural/repetition evidence.
        if (providerMediaSignature) score += 4;
        if (repeatedMediaLocation) score += 4;
        else if (pod.segmentCount >= 2 && matchingDurations >= 2) score += 2;
        if (repeatedForeignDirectory && !repeatedMediaLocation) score += 1;
        // A repeated fingerprint only becomes actionable with an independent
        // change in media location or numbering and enough surrounding content.
        if ((repeatedMediaLocation || repeatedForeignDirectory) && pathDisparity && pod.duration <= 30.0) score += 3;
        else if (pod.segmentCount >= 2 && matchingDurations >= 2 && longNeighbors && indexJump) score += 4;

        if (shouldUseWeakDiscontinuitySignal(records)) {
            boolean durationAnomaly = hasSegmentDurationAnomaly(records, pod);
            boolean longShortPattern = isLongShortAlternatingPattern(pods, pod);

            boolean hasDisparityOrAnomaly = pathDisparity || indexJump || durationAnomaly;
            if ((longNeighbors || longShortPattern) && hasDisparityOrAnomaly
                    && !((repeatedMediaLocation || repeatedForeignDirectory) && pathDisparity && pod.duration <= 30.0)
                    && !(pod.segmentCount >= 2 && matchingDurations >= 2 && longNeighbors && indexJump)) {
                score += 3;
            }

            // Public TVBox rules for these sources all describe short blocks
            // delimited by discontinuities. Do not delete every such block: RFC
            // 8216 also uses DISCONTINUITY for legitimate codec/timestamp changes.
            // Require sparse discontinuities, long content neighbours and another
            // independent anomaly before applying the source-specific boost.
            if (isKnownStructuralInsertionProvider(provider) && longNeighbors
                    && hasDisparityOrAnomaly && pod.duration <= 120.0) score += 3;
            if ("ikun".equals(provider) && (pod.clearKeyExcursionSignal || pod.keySwitchSignal || pathDisparity)) score += 2;
        }

        return score;
    }

    private static boolean isKnownStructuralInsertionProvider(String provider) {
        return "liangzi".equals(provider)
                || "feifan".equals(provider)
                || "suoni".equals(provider)
                || "baofeng".equals(provider)
                || "wolong".equals(provider)
                || "jisu".equals(provider)
                || "xinsj".equals(provider)
                || "haiwaikan".equals(provider)
                || "ryplay".equals(provider);
    }

    private static boolean hasProviderMediaSignature(List<Record> records, Pod pod, String provider) {
        if (records == null || pod == null || isEmpty(provider)) return false;
        int segments = 0;
        int matches = 0;
        for (int i = pod.start; i < pod.end; i++) {
            Record record = records.get(i);
            if (!record.segment) continue;
            segments++;
            if (matchesProviderMediaSignature(record.resolvedUri, provider)) matches++;
        }
        if (segments == 0) return false;
        // The public 量子 rule is a long random [a-z0-9]{18,}.ts filename.
        // Requiring most of a multi-segment pod to match avoids treating an
        // ordinary hashed content segment as an ad on its own.
        if ("liangzi".equals(provider)) return matches >= Math.max(1, (segments + 1) / 2);
        return matches > 0;
    }

    private static boolean matchesProviderMediaSignature(String uri, String provider) {
        if (isEmpty(uri)) return false;
        String decoded = safeDecode(uri).toLowerCase(Locale.US);
        String path = stripQueryAndFragment(mediaPathAndQuery(decoded));
        String stem = filenameStem(decoded).toLowerCase(Locale.US);

        if ("liangzi".equals(provider)) return isLongAlphaNumericStem(stem);
        if ("suoni".equals(provider)) {
            return decoded.contains("p1ayer") || path.contains("/video/original") || stem.contains("1o");
        }
        if ("jisu".equals(provider) || "xinsj".equals(provider)) {
            return decoded.contains("muguacdn") || stem.contains("1o");
        }
        if ("baofeng".equals(provider) || "ryplay".equals(provider)) {
            return decoded.contains("adjump") || stem.contains("1o");
        }
        return false;
    }

    private static boolean isLongAlphaNumericStem(String stem) {
        if (stem == null || stem.length() < 18 || stem.length() > 96) return false;
        boolean hasLetter = false;
        boolean hasDigit = false;
        for (int i = 0; i < stem.length(); i++) {
            char c = stem.charAt(i);
            if (c >= 'a' && c <= 'z') hasLetter = true;
            else if (c >= '0' && c <= '9') hasDigit = true;
            else return false;
        }
        return hasLetter && hasDigit;
    }

    private static boolean hasRepeatedPodDirectory(List<Record> records, List<Pod> pods, Pod pod) {
        String directory = extractDirectory(records.get(pod.start).resolvedUri);
        if (directory.isEmpty()) return false;
        for (Pod other : pods) {
            if (other != pod && directory.equals(extractDirectory(records.get(other.start).resolvedUri))) return true;
        }
        return false;
    }

    private static boolean hasSegmentDurationAnomaly(List<Record> records, Pod pod) {
        if (pod.end <= pod.start) return false;

        int podSegCount = 0;
        double podDurationSum = 0;
        for (int i = pod.start; i < pod.end; i++) {
            Record r = records.get(i);
            if (r.segment) {
                podSegCount++;
                podDurationSum += r.duration;
            }
        }
        if (podSegCount == 0) return false;
        double podAvgDur = podDurationSum / podSegCount;

        int mainSegCount = 0;
        double mainDurationSum = 0;
        for (int i = pod.start - 1; i >= 0; i--) {
            Record r = records.get(i);
            if (r.segment) {
                mainSegCount++;
                mainDurationSum += r.duration;
            }
        }
        for (int i = pod.end; i < records.size(); i++) {
            Record r = records.get(i);
            if (r.segment) {
                mainSegCount++;
                mainDurationSum += r.duration;
            }
        }
        if (mainSegCount < 5) return false;
        double mainAvgDur = mainDurationSum / mainSegCount;

        return mainAvgDur >= 6.0 && podAvgDur <= 3.5 && (mainAvgDur / podAvgDur >= 2.0);
    }

    private static boolean hasLongMainNeighborOrBoundary(List<Record> records, int index, int step) {
        if (step < 0 && index < 0) return true;
        if (step > 0 && index >= records.size()) return true;
        int segments = 0;
        double duration = 0;
        for (int i = index; i >= 0 && i < records.size(); i += step) {
            Record record = records.get(i);
            if (!record.segment) continue;
            boolean boundary = hasTagPrefix(record.tags, "#EXT-X-DISCONTINUITY");
            if (step > 0 && segments > 0 && boundary) return false;
            duration += record.duration;
            if (++segments == 20) return duration >= 60;
            if (step < 0 && boundary) {
                if (i <= 5) return true;
                return false;
            }
        }
        return (step < 0 && index < 20) || (step > 0 && index >= records.size() - 20);
    }

    private static boolean hasPathDisparity(List<Record> records, Pod pod) {
        Set<String> podDirs = new HashSet<>();
        for (int i = pod.start; i < pod.end; i++) {
            Record r = records.get(i);
            if (r.segment) {
                String dir = extractDirectory(r.resolvedUri);
                if (!dir.isEmpty()) podDirs.add(dir);
            }
        }
        if (podDirs.isEmpty()) return false;

        Set<String> neighborDirs = new HashSet<>();
        for (int i = pod.start - 1; i >= 0; i--) {
            Record r = records.get(i);
            if (r.segment) {
                String dir = extractDirectory(r.resolvedUri);
                if (!dir.isEmpty()) neighborDirs.add(dir);
                break;
            }
        }
        for (int i = pod.end; i < records.size(); i++) {
            Record r = records.get(i);
            if (r.segment) {
                String dir = extractDirectory(r.resolvedUri);
                if (!dir.isEmpty()) neighborDirs.add(dir);
                break;
            }
        }

        if (neighborDirs.isEmpty()) return false;

        for (String pDir : podDirs) {
            if (neighborDirs.contains(pDir)) return false;
        }
        return true;
    }

    private static String extractDirectory(String uri) {
        if (isEmpty(uri)) return "";
        String clean = stripQueryAndFragment(uri);
        int lastSlash = clean.lastIndexOf('/');
        if (lastSlash > 0) {
            return clean.substring(0, lastSlash + 1);
        }
        return clean;
    }

    private static boolean hasIndexJump(List<Record> records, Pod pod) {
        long numBefore = -1;
        for (int i = pod.start - 1; i >= 0; i--) {
            Record r = records.get(i);
            if (r.segment) {
                numBefore = extractFilenameNumber(r.resolvedUri);
                if (numBefore >= 0) break;
            }
        }

        long numAfter = -1;
        for (int i = pod.end; i < records.size(); i++) {
            Record r = records.get(i);
            if (r.segment) {
                numAfter = extractFilenameNumber(r.resolvedUri);
                if (numAfter >= 0) break;
            }
        }

        if (numBefore < 0 || numAfter < 0) return false;

        long diff = numAfter - numBefore;
        if (diff >= 1 && diff <= 10) {
            for (int i = pod.start; i < pod.end; i++) {
                Record r = records.get(i);
                if (r.segment) {
                    long podNum = extractFilenameNumber(r.resolvedUri);
                    if (podNum < numBefore || podNum > numAfter) return true;
                }
            }
        }
        return false;
    }

    private static boolean hasBoundedNumericExcursion(List<Record> records, Pod pod) {
        if (pod.duration < 10.0 || pod.duration > 40.0) return false;
        Record before = null;
        Record after = null;
        for (int i = pod.start - 1; i >= 0; i--) {
            if (records.get(i).segment) { before = records.get(i); break; }
        }
        for (int i = pod.end; i < records.size(); i++) {
            if (records.get(i).segment) { after = records.get(i); break; }
        }
        if (before == null || after == null) return false;
        String directory = extractDirectory(before.resolvedUri);
        if (directory.isEmpty() || !directory.equals(extractDirectory(after.resolvedUri))) return false;
        String prefix = filenameSequencePrefix(before.resolvedUri);
        if (prefix == null || !prefix.equals(filenameSequencePrefix(after.resolvedUri))) return false;
        long beforeNumber = extractFilenameSequenceNumber(before.resolvedUri);
        long afterNumber = extractFilenameSequenceNumber(after.resolvedUri);
        if (beforeNumber < 0 || afterNumber != beforeNumber + 1) return false;

        int segments = 0;
        long previous = -1;
        for (int i = pod.start; i < pod.end; i++) {
            Record record = records.get(i);
            if (!record.segment) continue;
            if (isPartOnlyRecord(record) || !directory.equals(extractDirectory(record.resolvedUri))
                    || !prefix.equals(filenameSequencePrefix(record.resolvedUri))) return false;
            long number = extractFilenameSequenceNumber(record.resolvedUri);
            if (number < 0 || (segments > 0 && number != previous + 1)) return false;
            if (segments == 0 && number - beforeNumber < 1000 && beforeNumber - number < 1000) return false;
            previous = number;
            segments++;
        }
        return segments >= 5 && segments <= 10;
    }

    private static String filenameSequencePrefix(String uri) {
        String stem = filenameStem(uri);
        if (stem.isEmpty()) return null;
        int start = stem.length();
        while (start > 0 && Character.isDigit(stem.charAt(start - 1))) start--;
        int digits = stem.length() - start;
        if (digits == 0 || digits > 15 || start > 12) return null;
        return stem.substring(0, start);
    }

    private static long extractFilenameSequenceNumber(String uri) {
        String stem = filenameStem(uri);
        String prefix = filenameSequencePrefix(uri);
        if (prefix == null) return -1;
        try {
            return Long.parseLong(stem.substring(prefix.length()));
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    private static String filenameStem(String uri) {
        if (isEmpty(uri)) return "";
        String clean = stripQueryAndFragment(uri);
        int lastSlash = clean.lastIndexOf('/');
        String filename = lastSlash >= 0 ? clean.substring(lastSlash + 1) : clean;
        int dot = filename.lastIndexOf('.');
        return dot > 0 ? filename.substring(0, dot) : filename;
    }

    private static long extractFilenameNumber(String uri) {
        if (isEmpty(uri)) return -1;
        String stem = filenameStem(uri);

        if (isHexString(stem)) return -1;

        String[] tokens = stem.split("[^0-9]+");
        for (int k = tokens.length - 1; k >= 0; k--) {
            String token = tokens[k];
            if (token.isEmpty() || token.length() > 10) continue;
            try {
                return Long.parseLong(token);
            } catch (Throwable ignored) {
            }
        }
        return -1;
    }

    private static boolean isHexString(String s) {
        if (s == null || s.length() < 12) return false;
        boolean hasLetter = false;
        boolean hasDigit = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if ((c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F')) hasLetter = true;
            else if (c >= '0' && c <= '9') hasDigit = true;
            else return false;
        }
        return hasLetter && hasDigit;
    }

    private static boolean isLongShortAlternatingPattern(List<Pod> pods, Pod pod) {
        if (pods == null || pods.size() < 3 || pods.size() > 8) return false;

        double podDuration = pod.duration;
        int longCount = 0;

        for (Pod p : pods) {
            if (p == pod) continue;
            if (p.duration >= Math.max(60.0, podDuration * 3.0)) {
                longCount++;
            }
        }

        return longCount >= 1 && podDuration <= 45.0;
    }

    private static void addPod(List<Pod> pods, List<Record> records, int start, int end, String beforeKey, String afterKey) {
        Pod pod = Pod.create(records, start, end, beforeKey, afterKey);
        if (pod != null) pods.add(pod);
    }

    static boolean shouldEndCue(double declaredSeconds, int removedSegments) {
        return shouldEndCue(declaredSeconds, removedSegments, false);
    }

    static boolean shouldEndCue(double declaredSeconds, int removedSegments, boolean hasClosingTag) {
        if (hasClosingTag) return removedSegments >= MAX_CONTIGUOUS_AD_SEGMENTS;
        int limit = declaredSeconds <= 0 ? MAX_UNBOUNDED_CUE_AD_SEGMENTS : MAX_CONTIGUOUS_AD_SEGMENTS;
        return removedSegments >= limit;
    }

    static boolean shouldRemoveRepeatedPod(int matchingPods, boolean strongAdSignal) {
        return strongAdSignal && matchingPods >= 2;
    }

    static boolean shouldRemoveIdenticalMediaPod(int matchingPods) {
        return matchingPods >= 2;
    }

    static boolean shouldRemoveKeySwitchPod(boolean keySwitchSignal) {
        return keySwitchSignal;
    }

    private static boolean shouldKeepOriginalForHlsIntegrity(String original, int originalSegmentCount, int filteredSegmentCount) {
        if (originalSegmentCount <= filteredSegmentCount) return false;
        return hasSequenceDerivedEncryptionKey(original) || hasImplicitByteRange(original);
    }

    private static boolean hasImplicitByteRange(String playlist) {
        for (String line : playlist.split("\n", -1)) {
            String value = trimLineEnd(line).trim();
            if (value.startsWith("#EXT-X-BYTERANGE:") && value.indexOf('@') < 0) return true;
        }
        return false;
    }

    private static boolean hasSequenceDerivedEncryptionKey(String playlist) {
        if (isEmpty(playlist)) return false;
        for (String line : playlist.split("\n", -1)) {
            String value = trimLineEnd(line).trim();
            if (!value.startsWith("#EXT-X-KEY:")) continue;
            String method = parseAttributeString(value, "METHOD");
            String keyFormat = parseAttributeString(value, "KEYFORMAT");
            if (("AES-128".equalsIgnoreCase(method) || "SAMPLE-AES".equalsIgnoreCase(method))
                    && (isEmpty(keyFormat) || "identity".equalsIgnoreCase(keyFormat))
                    && isEmpty(parseAttributeString(value, "IV"))) return true;
        }
        return false;
    }

    private static List<Record> filterShortAdClusters(List<Record> records) {
        if (records == null || records.isEmpty()) return records;

        List<Record> kept = new ArrayList<>(records.size());
        List<Record> cluster = new ArrayList<>();

        int segmentCount = countSegments(records);
        if (segmentCount < 4) return records;

        for (int i = 0; i < records.size(); i++) {
            Record record = records.get(i);
            if (!record.segment) {
                flushCluster(cluster, kept, segmentCount, records, i);
                kept.add(record);
                continue;
            }

            if (isExplicitAdRecord(record) || record.adLikeUri) {
                cluster.add(record);
            } else {
                flushCluster(cluster, kept, segmentCount, records, i);
                kept.add(record);
            }
        }

        flushCluster(cluster, kept, segmentCount, records, records.size());

        if (countSegments(kept) <= 0) return records;
        return kept;
    }

    private static void flushCluster(List<Record> cluster, List<Record> kept, int segmentCount,
                                     List<Record> records, int end) {
        if (cluster.isEmpty()) return;

        int clusterSize = cluster.size();
        double duration = 0;
        for (Record record : cluster) duration += record.duration;

        boolean hasExplicitAd = hasAnyExplicitAd(cluster);
        boolean hasCorroboration = hasStructuralCorroboration(cluster, records, end);
        boolean looksLikeAdCluster = clusterSize <= Math.max(1, segmentCount / 3)
                && (hasExplicitAd || (clusterSize >= 2 && duration <= 120 && hasCorroboration));

        if (!looksLikeAdCluster) kept.addAll(cluster);

        cluster.clear();
    }

    private static boolean hasStructuralCorroboration(List<Record> cluster, List<Record> records, int end) {
        int start = end - cluster.size();
        String activeKey = "";
        for (int i = 0; i < start; i++) {
            String key = Pod.lastKey(records.get(i).tags);
            if (!key.isEmpty()) activeKey = key;
        }
        for (Record record : cluster) {
            if (hasTagPrefix(record.tags, "#EXT-X-DISCONTINUITY")) return true;
            String key = Pod.lastKey(record.tags);
            if (!key.isEmpty() && !key.equals(activeKey)) return true;
            if (!key.isEmpty()) activeKey = key;
        }
        int previousSegment = start - 1;
        while (previousSegment >= 0 && !records.get(previousSegment).segment) previousSegment--;
        int nextSegment = end;
        while (nextSegment < records.size() && !records.get(nextSegment).segment) nextSegment++;
        if (nextSegment < records.size()) {
            Record next = records.get(nextSegment);
            if (hasTagPrefix(next.tags, "#EXT-X-DISCONTINUITY")) return true;
            String key = Pod.lastKey(next.tags);
            if (!key.isEmpty() && !key.equals(activeKey)) return true;
        }
        if (previousSegment >= 0 && nextSegment < records.size()) {
            String clusterDir = extractDirectory(cluster.get(0).resolvedUri);
            String beforeDir = extractDirectory(records.get(previousSegment).resolvedUri);
            String afterDir = extractDirectory(records.get(nextSegment).resolvedUri);
            if (!clusterDir.isEmpty() && !beforeDir.isEmpty() && !afterDir.isEmpty()
                    && !clusterDir.equals(beforeDir) && !clusterDir.equals(afterDir)) return true;
        }
        return false;
    }

    private static boolean hasAnyExplicitAd(List<Record> records) {
        for (Record record : records) {
            if (isExplicitAdRecord(record)) return true;
        }
        return false;
    }

    private static int countSegments(List<Record> records) {
        int count = 0;
        for (Record record : records) {
            if (record.segment) count++;
        }
        return count;
    }

    private static boolean isStandaloneAdResourceTag(String line, String baseUrl) {
        if (isEmpty(line)) return false;

        String lower = line.toLowerCase(Locale.US);

        if (!lower.startsWith("#ext-x-part:")
                && !lower.startsWith("#ext-x-preload-hint:")
                && !lower.startsWith("#ext-x-map:")
                && !lower.startsWith("#ext-x-asset-list:")
                && !lower.startsWith("#ext-x-rendition-report:")) return false;

        String uri = parseAttributeString(line, "URI");
        if (isEmpty(uri)) return false;

        return isLikelyAdResourceUri(resolveUri(baseUrl, uri));
    }

    private static boolean isPlainAdResourceUri(String line, String baseUrl) {
        if (isEmpty(line)) return false;
        String value = line.trim();
        if (value.isEmpty() || value.startsWith("#")) return false;
        return isLikelyAdResourceUri(resolveUri(baseUrl, value));
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
            String line = trimLineEnd(raw);

            if (isPartTag(line)) {
                String uri = parseAttributeString(line, "URI");
                String resolved = resolveUri(baseUrl, uri);
                List<String> tags = new ArrayList<>(pending);
                tags.add(line);
                addSegmentRecord(records, tags, uri, resolved, parsePartDuration(line));
                pending.clear();
                continue;
            }

            if (isSegmentTag(line) || (!pending.isEmpty() && isSegmentFollowTag(line))) {
                pending.add(line);
                continue;
            }

            if (!pending.isEmpty()) {
                if (!line.startsWith("#") && !line.trim().isEmpty()) {
                    String resolved = resolveUri(baseUrl, line);
                    addSegmentRecord(records, new ArrayList<>(pending), line, resolved, parseExtInfDuration(pending));
                    pending.clear();
                    continue;
                }

                for (String tag : pending) records.add(Record.plain(tag));
                pending.clear();
            }

            records.add(Record.plain(line));
        }

        if (!pending.isEmpty()) {
            for (String tag : pending) records.add(Record.plain(tag));
        }

        return records;
    }

    private static void addSegmentRecord(List<Record> records, List<String> tags, String uri,
                                         String resolved, double duration) {
        // Cue markers may occur between EXTINF and its URI. They still change
        // interval state, rather than merely marking that single segment as an ad.
        List<String> segmentTags = new ArrayList<>();
        for (String tag : tags) {
            if (isCueOutTag(tag) || isCueInTag(tag) || tag.startsWith("#EXT-X-DATERANGE:")) {
                records.add(Record.plain(tag));
            }
            else segmentTags.add(tag);
        }
        records.add(Record.segment(segmentTags, uri, resolveHostFromResolved(resolved), resolved, duration));
    }

    private static boolean isSegmentTag(String line) {
        return line.startsWith("#EXTINF")
                || line.equals("#EXT-X-DISCONTINUITY")
                || line.startsWith("#EXT-X-PROGRAM-DATE-TIME")
                || line.startsWith("#EXT-X-KEY:")
                || line.startsWith("#EXT-X-BYTERANGE")
                || line.startsWith("#EXT-X-GAP");
    }

    private static boolean isSegmentFollowTag(String line) {
        return line.startsWith("#EXT-X-BYTERANGE")
                || line.startsWith("#EXT-X-GAP")
                || line.startsWith("#EXT-X-PROGRAM-DATE-TIME")
                || line.startsWith("#EXT-X-KEY:")
                || line.startsWith("#EXT-X-MAP:")
                || isCueInTag(line)
                || isExplicitAdTag(line);
    }

    private static boolean isPartTag(String line) {
        return line.startsWith("#EXT-X-PART:");
    }

    private static double parseExtInfDuration(List<String> tags) {
        for (String tag : tags) {
            if (!tag.startsWith("#EXTINF:")) continue;
            int start = "#EXTINF:".length();
            int end = tag.indexOf(',', start);
            String value = end > start ? tag.substring(start, end) : tag.substring(start);
            return parseDouble(value);
        }

        double partDuration = 0;
        for (String tag : tags) {
            if (tag.startsWith("#EXT-X-PART:")) partDuration += parsePartDuration(tag);
        }

        return partDuration;
    }

    private static double parsePartDuration(String line) {
        return parseAttributeDouble(line, "DURATION");
    }

    private static boolean isCueOutTag(String line) {
        String lower = line.toLowerCase(Locale.US);
        return lower.startsWith("#ext-x-cue-out")
                || lower.startsWith("#ext-x-cue:")
                || lower.startsWith("#ext-cue-out")
                || lower.startsWith("#ext-vl-cue-out")
                || lower.startsWith("#ext-x-cue-span")
                || lower.startsWith("#ext-x-ad-start")
                || lower.startsWith("#ext-x-vmap-ad-break")
                || lower.startsWith("#ext-x-scte35-out")
                || isScte35CueDirection(line, "out");
    }

    private static boolean isCueInTag(String line) {
        String lower = line.toLowerCase(Locale.US);
        return lower.startsWith("#ext-x-cue-in")
                || lower.startsWith("#ext-cue-in")
                || lower.startsWith("#ext-vl-cue-in")
                || lower.startsWith("#ext-x-ad-end")
                || lower.startsWith("#ext-x-scte35-in")
                || isScte35CueDirection(line, "in");
    }

    private static boolean isScte35CueDirection(String line, String direction) {
        if (isEmpty(line) || isEmpty(direction)) return false;
        String lower = line.toLowerCase(Locale.US);
        if (!lower.startsWith("#ext-x-scte35:")) return false;

        String cue = parseAttributeString(line, "CUE");
        if (matchesCueDirection(cue, direction)) return true;

        String type = parseAttributeString(line, "TYPE");
        if (matchesCueDirection(type, direction)) return true;

        String event = parseAttributeString(line, "EVENT");
        return matchesCueDirection(event, direction);
    }

    private static boolean matchesCueDirection(String value, String direction) {
        if (isEmpty(value)) return false;
        String[] tokens = value.toLowerCase(Locale.US).split("[^a-z0-9]+");
        for (String token : tokens) {
            if (token.equals(direction)) return true;
            if ("out".equals(direction) && ("cueout".equals(token) || "start".equals(token))) return true;
            if ("in".equals(direction) && ("cuein".equals(token) || "end".equals(token))) return true;
        }
        return false;
    }

    private static boolean isAdDateRangeTag(String line) {
        if (!line.startsWith("#EXT-X-DATERANGE")) return false;

        String id = parseAttributeString(line, "ID");
        String clazz = parseAttributeString(line, "CLASS");
        String type = parseAttributeString(line, "TYPE");
        String asset = parseAttributeString(line, "X-ASSET");
        String assetUri = parseAttributeString(line, "X-ASSET-URI");
        String assetList = parseAttributeString(line, "X-ASSET-LIST");
        String contentType = parseAttributeString(line, "X-CONTENT-TYPE");
        String placement = parseAttributeString(line, "X-PLACEMENT");
        String playbackRestriction = parseAttributeString(line, "X-PLAYBACK-RESTRICTION");
        String adUrl = parseAttributeString(line, "X-AD-URL");
        String adId = parseAttributeString(line, "X-AD-ID");
        String scte = parseAttributeString(line, "SCTE35-OUT");
        String scteCmd = parseAttributeString(line, "SCTE35-CMD");

        return containsAdKeyword(id)
                || containsAdKeyword(clazz)
                || containsAdKeyword(type)
                || containsAdKeyword(asset)
                || containsAdKeyword(assetUri)
                || containsAdKeyword(assetList)
                || containsAdKeyword(contentType)
                || containsAdKeyword(placement)
                || containsAdKeyword(playbackRestriction)
                || containsAdKeyword(adUrl)
                || containsAdKeyword(adId)
                || isLikelyAdResourceUri(assetUri)
                || isLikelyAdResourceUri(assetList)
                || isLikelyAdResourceUri(adUrl)
                || !isEmpty(scte)
                || !isEmpty(scteCmd)
                || hasAttributeKeyPrefix(line, "X-AD-");
    }

    private static boolean isAdSignalLine(String line) {
        if (isEmpty(line)) return false;

        String lower = line.toLowerCase(Locale.US);

        if (lower.startsWith("#ext-oatcls-scte35")) return true;
        if (lower.startsWith("#ext-x-scte35")) return true;
        if (lower.startsWith("#ext-x-splicepoint-scte35")) return true;
        if (lower.startsWith("#ext-x-asset:")) return true;
        if (lower.startsWith("#ext-x-ad")) return true;
        if (lower.startsWith("#ext-x-vast")) return true;
        if (lower.startsWith("#ext-x-vmap")) return true;

        return false;
    }

    private static double parseCueOutDuration(String line) {
        int index = line.indexOf(':');
        if (index > -1 && index < line.length() - 1) {
            String part = line.substring(index + 1).trim();
            if (!part.contains("=") && !part.contains("/")) return parseDouble(part);
        }

        double duration = parseAttributeDouble(line, "DURATION");
        if (duration <= 0) duration = parseAttributeDouble(line, "PLANNED-DURATION");
        if (duration <= 0) duration = parseAttributeDouble(line, "TOTAL-DURATION");
        double elapsed = parseAttributeDouble(line, "ElapsedTime");
        // Sliding windows can begin in the middle of an ad break.
        if (line.toUpperCase(Locale.US).contains("-OUT-CONT:")) {
            int colon = line.indexOf(':');
            String value = line.substring(colon + 1).trim();
            int slash = value.indexOf('/');
            if (slash > 0 && !value.contains("=")) {
                elapsed = parseDouble(value.substring(0, slash));
                duration = parseDouble(value.substring(slash + 1));
            }
            // Negative means explicitly complete; zero means duration unknown.
            return duration > 0 ? (elapsed >= duration ? -1 : duration - elapsed) : 0;
        }
        return duration;
    }

    private static int indexOfKey(String line, String key) {
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') quoted = !quoted;
            if (quoted) continue;
            if (i > 0 && line.charAt(i - 1) != ',' && line.charAt(i - 1) != ':'
                    && line.charAt(i - 1) != ' ') continue;
            if (line.regionMatches(true, i, key + "=", 0, key.length() + 1)) return i;
        }
        return -1;
    }

    private static double parseAttributeDouble(String line, String key) {
        String value = parseAttributeString(line, key);
        if (isEmpty(value)) return 0;
        return parseDouble(value);
    }

    private static String parseAttributeString(String line, String key) {
        if (isEmpty(line) || isEmpty(key)) return "";

        int idx = indexOfKey(line, key);
        if (idx < 0) return "";

        int start = idx + key.length() + 1;
        if (start >= line.length()) return "";

        int end = start;
        boolean quoted = line.charAt(start) == '"';

        if (quoted) {
            start++;
            end = line.indexOf('"', start);
            if (end < 0) end = line.length();
        } else {
            while (end < line.length()
                    && line.charAt(end) != ','
                    && line.charAt(end) != '\r'
                    && line.charAt(end) != '\n') {
                end++;
            }
        }

        return line.substring(start, end).trim();
    }

    private static boolean hasAttributeKeyPrefix(String line, String prefix) {
        if (isEmpty(line) || isEmpty(prefix)) return false;

        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            if (line.charAt(i) == '"') quoted = !quoted;
            if (!quoted && (i == 0 || line.charAt(i - 1) == ',' || line.charAt(i - 1) == ':'
                    || line.charAt(i - 1) == ' ')
                    && line.regionMatches(true, i, prefix, 0, prefix.length())) return true;
        }

        return false;
    }

    private static boolean containsAdKeyword(String text) {
        if (isEmpty(text)) return false;

        String value = text.toLowerCase(Locale.US)
                .replace('-', ' ')
                .replace('_', ' ')
                .replace('.', ' ')
                .replace('/', ' ')
                .replace('%', ' ');

        String[] tokens = value.split("[^a-z0-9]+");

        for (String token : tokens) {
            if (token.isEmpty()) continue;
            if (STRONG_AD_KEYWORDS.contains(token) || WEAK_AD_KEYWORDS.contains(token)) return true;

            if (token.startsWith("ad") && token.length() <= 12) {
                if (token.equals("ad") || token.equals("ads") || token.equals("adv")) return true;
                if (token.contains("roll") || token.contains("pod") || token.contains("break")) return true;
            }

            if (hasEndingAdWord(token)) return true;
        }

        return false;
    }

    private static boolean containsStrongAdKeyword(String text) {
        if (isEmpty(text)) return false;
        String[] tokens = text.toLowerCase(Locale.US).split("[^a-z0-9]+");
        for (String token : tokens) {
            if (STRONG_AD_KEYWORDS.contains(token) || hasEndingAdWord(token)) return true;
        }
        return false;
    }

    private static boolean isExplicitAdRecord(Record record) {
        return record != null && record.explicitAd;
    }

    private static boolean isExplicitAdSegment(List<String> tags, int uriAdSignals) {
        if (hasExplicitAdTag(tags)) return true;
        if (hasExplicitAdResourceTag(tags)) return true;
        return (uriAdSignals & URI_STRONG_AD) != 0;
    }

    private static boolean hasExplicitAdResourceTag(List<String> tags) {
        if (tags == null || tags.isEmpty()) return false;
        for (String tag : tags) {
            if (isEmpty(tag)) continue;
            String lower = tag.toLowerCase(Locale.US);
            // MAP is required initialization state, wherever it appears relative
            // to EXTINF. Its filename alone does not classify the media as an ad.
            if (!lower.startsWith("#ext-x-part:")
                    && !lower.startsWith("#ext-x-preload-hint:")) continue;
            String uri = parseAttributeString(tag, "URI");
            if (!isEmpty(uri) && isLikelyAdResourceUri(uri)) return true;
        }
        return false;
    }

    private static boolean hasExplicitAdTag(List<String> tags) {
        if (tags == null || tags.isEmpty()) return false;
        for (String tag : tags) {
            if (isEmpty(tag)) continue;
            if (isExplicitAdTag(tag)) return true;
        }
        return false;
    }

    private static boolean hasAdSignalTag(List<String> tags) {
        if (tags == null || tags.isEmpty()) return false;
        for (String tag : tags) {
            if (isEmpty(tag)) continue;
            if (isAdSignalTag(tag)) return true;
        }
        return false;
    }

    private static boolean isExplicitAdTag(String tag) {
        String lower = tag.toLowerCase(Locale.US);

        if (lower.startsWith("#ext-x-cue:")) return true;
        if (lower.startsWith("#ext-x-cue-out")) return true;
        if (lower.startsWith("#ext-x-cue-out-cont")) return true;
        if (lower.startsWith("#ext-cue-out")) return true;
        if (lower.startsWith("#ext-vl-cue-out")) return true;
        if (lower.startsWith("#ext-x-cue-span")) return true;
        if (lower.startsWith("#ext-oatcls-scte35")) return true;
        if (lower.startsWith("#ext-x-scte35")) return true;
        if (lower.startsWith("#ext-x-splicepoint-scte35")) return true;
        if (lower.startsWith("#ext-x-asset:")) return true;
        if (lower.startsWith("#ext-x-ad")) return true;
        if (lower.startsWith("#ext-x-vast")) return true;
        if (lower.startsWith("#ext-x-vmap")) return true;

        if (lower.startsWith("#ext-x-daterange")) return isAdDateRangeTag(tag);

        return false;
    }

    private static boolean isAdSignalTag(String tag) {
        String lower = tag.toLowerCase(Locale.US);
        return lower.startsWith("#ext-x-discontinuity")
                || lower.startsWith("#ext-x-program-date-time")
                || lower.startsWith("#ext-x-map:")
                || lower.startsWith("#ext-x-byterange")
                || lower.startsWith("#ext-x-gap")
                || lower.startsWith("#ext-x-part:")
                || lower.startsWith("#ext-x-preload-hint:");
    }

    private static boolean hasTagPrefix(List<String> tags, String prefix) {
        if (tags == null || tags.isEmpty()) return false;
        for (String tag : tags) {
            if (tag != null && tag.startsWith(prefix)) return true;
        }
        return false;
    }

    private static String lastTag(List<String> tags, String prefix) {
        if (tags == null || tags.isEmpty()) return "";
        String match = "";
        for (String tag : tags) {
            if (tag != null && tag.startsWith(prefix)) match = tag;
        }
        return match;
    }

    private static boolean isLikelyAdSegmentUri(String uri) {
        if (isEmpty(uri)) return false;

        String lower = uri.toLowerCase(Locale.US).trim();
        int fragment = lower.indexOf('#');
        if (fragment >= 0) lower = lower.substring(0, fragment);

        if (!isLikelySegmentResource(lower)) return false;

        return isAdLikeUri(lower);
    }

    private static boolean isLikelyAdResourceUri(String uri) {
        if (isEmpty(uri)) return false;

        String lower = uri.toLowerCase(Locale.US).trim();
        int fragment = lower.indexOf('#');
        if (fragment >= 0) lower = lower.substring(0, fragment);

        if (isLikelySegmentResource(lower) || isLikelyPlaylistResource(lower) || stripQueryAndFragment(lower).endsWith(".json")) {
            return isAdLikeUri(lower);
        }

        return containsAdKeyword(mediaPathAndQuery(lower)) || containsAdQueryKey(lower);
    }

    private static int classifySegmentUri(String uri) {
        if (isEmpty(uri)) return 0;

        String lower = uri.toLowerCase(Locale.US).trim();
        int fragment = lower.indexOf('#');
        if (fragment >= 0) lower = lower.substring(0, fragment);

        if (!isLikelySegmentResource(lower)) return 0;
        if (containsAdJumpMediaPath(lower)) return URI_AD_LIKE | URI_STRONG_AD;
        if (isStrongAdLikeUri(lower)) return URI_AD_LIKE | URI_STRONG_AD;
        return containsAdPathPart(lower) ? URI_AD_LIKE : 0;
    }

    private static boolean containsAdNumberMediaPath(String uri) {
        if (isEmpty(uri) || !isLikelySegmentResource(uri)) return false;
        String path = stripQueryAndFragment(mediaPathAndQuery(uri)).toLowerCase(Locale.US);
        for (String part : path.split("[/._\\-]+")) {
            if (isAdNumber(part, "ad") || isAdNumber(part, "ads")) return true;
        }
        return false;
    }

    private static boolean containsAdJumpMediaPath(String uri) {
        if (isEmpty(uri) || !isLikelySegmentResource(uri)) return false;
        String path = stripQueryAndFragment(mediaPathAndQuery(uri)).toLowerCase(Locale.US);
        for (String part : path.split("[/._\\-]+")) {
            if ("adjump".equals(part)) return true;
        }
        return false;
    }

    private static boolean isStrongAdLikeUri(String uri) {
        if (isEmpty(uri)) return false;

        String lower = uri.toLowerCase(Locale.US).trim();
        if (containsStrongAdQueryKey(lower)) return true;
        return containsStrongAdKeyword(safeDecode(stripQueryAndFragment(mediaPathAndQuery(lower))));
    }

    private static boolean isAdLikeUri(String uri) {
        if (isEmpty(uri)) return false;

        String lower = uri.toLowerCase(Locale.US).trim();

        if (containsAdQueryKey(lower)) return true;
        if (containsAdKeyword(safeDecode(stripQueryAndFragment(mediaPathAndQuery(lower))))) return true;
        if (containsAdPathPart(lower)) return true;
        try {
            if (AdBlocker.isAdUrl(lower)) return true;
        } catch (RuntimeException ignored) {
            // Keep playlist filtering available when the optional URL blocker cannot parse a URI.
        }

        return false;
    }

    static boolean containsAdQueryKey(String uri) {
        return containsAdQueryKey(uri, false);
    }

    private static boolean containsStrongAdQueryKey(String uri) {
        return containsAdQueryKey(uri, true);
    }

    private static boolean containsAdQueryKey(String uri, boolean strongOnly) {
        int fragment = uri.indexOf('#');
        int end = fragment >= 0 ? fragment : uri.length();
        int query = uri.indexOf('?');
        if (query < 0 || query >= end - 1) return false;

        for (int start = query + 1; start < end; ) {
            int separator = uri.indexOf('&', start);
            if (separator < 0 || separator > end) separator = end;
            int equals = uri.indexOf('=', start);
            if (equals < 0 || equals > separator) equals = separator;

            String key = safeDecode(uri.substring(start, equals)).toLowerCase(Locale.US);
            String value = equals < separator ? safeDecode(uri.substring(equals + 1, separator)).toLowerCase(Locale.US) : "";

            boolean adKey = strongOnly ? containsStrongAdKeyword(key)
                    : AD_QUERY_KEYS.contains(key) || containsAdKeyword(key);
            if (adKey && !isDisabledAdValue(value)) return true;

            if (isAdValueKey(key) && equals < separator) {
                if (strongOnly ? containsStrongAdKeyword(value) : containsAdKeyword(value)) return true;
            }
            if (equals < separator) {
                String rawValue = uri.substring(equals + 1, separator);
                if (isLikelyAdQueryValueResource(rawValue)
                        && (!strongOnly || containsStrongAdKeyword(safeDecode(rawValue)))) return true;
            }
            start = separator + 1;
        }

        return false;
    }

    private static boolean isDisabledAdValue(String value) {
        String normalized = value.trim();
        return normalized.isEmpty() || normalized.equals("0") || normalized.equals("false")
                || normalized.equals("off") || normalized.equals("no") || normalized.equals("none")
                || normalized.equals("null");
    }

    private static boolean isAdValueKey(String key) {
        return "type".equals(key)
                || "role".equals(key)
                || "class".equals(key)
                || "category".equals(key)
                || "asset".equals(key)
                || "content".equals(key)
                || "content_type".equals(key)
                || "content-type".equals(key)
                || "cust_params".equals(key)
                || "cust-params".equals(key)
                || "event".equals(key)
                || "iu".equals(key)
                || "kind".equals(key)
                || "label".equals(key)
                || "marker".equals(key)
                || "placement".equals(key)
                || "purpose".equals(key);
    }

    private static boolean isLikelyAdQueryValueResource(String value) {
        String decoded = safeDecode(value).toLowerCase(Locale.US).trim();
        if (isEmpty(decoded)) return false;
        if (!decoded.startsWith("http://")
                && !decoded.startsWith("https://")
                && !decoded.startsWith("//")
                && !decoded.contains("/")
                && !decoded.contains(".m3u")
                && !decoded.contains(".ts")
                && !decoded.contains(".m4s")
                && !decoded.contains(".mp4")) return false;
        return isLikelyAdResourceUri(decoded);
    }

    private static boolean isEmpty(CharSequence value) {
        return value == null || value.length() == 0;
    }

    private static boolean containsAdPathPart(String uri) {
        return containsAdPathPart(uri, true);
    }

    private static boolean containsAdPathPart(String uri, boolean includeAdNumbers) {
        String path = safeDecode(stripQueryAndFragment(mediaPathAndQuery(uri)));
        if (isEmpty(path)) return false;

        String[] parts = path.toLowerCase(Locale.US).split("[/._\\-]+");
        for (String part : parts) {
            if (isEmpty(part)) continue;
            if (AD_PATH_PARTS.contains(part)) return true;
            if (part.startsWith("ad") && part.length() <= 16
                    && (includeAdNumbers ? hasAdBoundaryWord(part) : hasStrongAdBoundaryWord(part))) return true;
            if (hasEndingAdWord(part)) return true;
            if (part.equals("adbreak") || part.equals("adpod")) return true;
        }

        return false;
    }

    private static boolean hasAdBoundaryWord(String value) {
        return hasStrongAdBoundaryWord(value)
                || isAdNumber(value, "ad")
                || isAdNumber(value, "ads");
    }

    private static boolean hasStrongAdBoundaryWord(String value) {
        return value.equals("ad")
                || value.equals("ads")
                || value.equals("adv")
                || value.startsWith("advert")
                || value.startsWith("adroll")
                || value.startsWith("adpod")
                || value.startsWith("adbreak")
                || value.startsWith("czad")
                || value.startsWith("guanggao")
                || value.startsWith("adjump")
                || value.startsWith("kudad")
                || value.startsWith("chaboad")
                || value.startsWith("kaipianad")
                || value.startsWith("piantouad")
                || value.startsWith("pianweiad")
                || value.startsWith("paomadeng")
                || value.startsWith("xinpujing")
                || value.startsWith("bet365");
    }

    private static boolean isAdNumber(String value, String prefix) {
        if (value.length() > prefix.length() && value.startsWith(prefix) && Character.isDigit(value.charAt(prefix.length()))) {
            return !isHexString(value);
        }
        return false;
    }

    private static boolean hasEndingAdWord(String value) {
        return value.equals("videoad") || value.equals("playad") || value.equals("preloadad");
    }

    private static String safeDecode(String value) {
        if (value == null) return "";
        try {
            String decoded = URLDecoder.decode(value, StandardCharsets.UTF_8.name());
            if (decoded.contains("%") && !decoded.equals(value)) {
                try {
                    decoded = URLDecoder.decode(decoded, StandardCharsets.UTF_8.name());
                } catch (Throwable ignored) {
                }
            }
            return decoded;
        } catch (Throwable ignored) {
            return value;
        }
    }

    private static boolean isLikelySegmentResource(String uri) {
        String path = stripQueryAndFragment(uri);

        return path.endsWith(".ts")
                || path.endsWith(".m4s")
                || path.endsWith(".mp4")
                || path.endsWith(".cmfa")
                || path.endsWith(".cmfv")
                || path.endsWith(".aac")
                || path.endsWith(".ac3")
                || path.endsWith(".ec3")
                || path.endsWith(".mp3")
                || path.endsWith(".mp2t")
                || path.endsWith(".mpeg")
                || path.endsWith(".mpg")
                || path.endsWith(".jpg")
                || path.endsWith(".jpeg")
                || path.endsWith(".vtt") == false && path.contains("/seg")
                || path.contains("/segment")
                || path.contains("/chunk")
                || path.contains("/part");
    }

    private static String stripQueryAndFragment(String uri) {
        if (uri == null) return "";
        String value = uri;
        int query = value.indexOf('?');
        if (query >= 0) value = value.substring(0, query);
        int hash = value.indexOf('#');
        if (hash >= 0) value = value.substring(0, hash);
        return value;
    }

    private static String mediaPathAndQuery(String uri) {
        if (isEmpty(uri)) return "";
        try {
            URI parsed = new URI(uri);
            String path = parsed.getRawPath();
            String query = parsed.getRawQuery();
            return (path == null ? "" : path) + (query == null ? "" : "?" + query);
        } catch (Throwable ignored) {
            int scheme = uri.indexOf("://");
            if (scheme < 0) return uri;
            int path = uri.indexOf('/', scheme + 3);
            return path < 0 ? "" : uri.substring(path);
        }
    }

    private static String exactMediaIdentity(String uri) {
        if (isEmpty(uri)) return "";
        String value = uri.trim();
        int fragment = value.indexOf('#');
        return fragment < 0 ? value : value.substring(0, fragment);
    }

    private static String exactMediaIdentity(Record record) {
        String identity = exactMediaIdentity(record.resolvedUri);
        String range = lastTag(record.tags, "#EXT-X-BYTERANGE");
        // The same resource commonly backs many distinct byte-range segments. An
        // omitted offset is derived from the preceding range, so the tag text alone
        // is not a stable media identity. Keep all byte-range records out of the
        // repeated-URI heuristic rather than risk deleting valid media.
        if (!isEmpty(range)) return "";
        String part = lastTag(record.tags, "#EXT-X-PART:");
        return isEmpty(parseAttributeString(part, "BYTERANGE")) ? identity : "";
    }

    private static double parseDouble(String value) {
        try {
            double parsed = Double.parseDouble(value.trim());
            return Double.isNaN(parsed) || Double.isInfinite(parsed) || parsed < 0 ? 0 : parsed;
        } catch (Throwable ignored) {
            return 0;
        }
    }

    /** Replay state changes in their original order before the next retained segment. */
    private static List<Record> restoreRetainedPlaybackState(List<Record> original, List<Record> kept) {
        if (original.size() == kept.size()) return kept;
        Set<Record> retained = new HashSet<>(kept);
        List<Record> restored = new ArrayList<>(kept.size());
        List<String> pendingState = new ArrayList<>();
        double time = Double.NaN;
        boolean needsTimeAnchor = false;
        for (Record record : original) {
            if (!record.segment) {
                if (record.line.equals("#EXT-X-DISCONTINUITY")) time = Double.NaN;
                if (record.line.startsWith("#EXT-X-PROGRAM-DATE-TIME:")) time = parseProgramTime(record.line);
                if (isPlaybackStateTag(record.line)) pendingState.add(record.line);
                else if (retained.contains(record)) restored.add(record);
                continue;
            }
            if (hasTagPrefix(record.tags, "#EXT-X-DISCONTINUITY")) time = Double.NaN;
            String anchor = lastTag(record.tags, "#EXT-X-PROGRAM-DATE-TIME:");
            if (!isEmpty(anchor)) time = parseProgramTime(anchor);
            double nextTime = record.duration > 0 ? time + record.duration * 1000 : Double.NaN;
            List<String> segmentTags = new ArrayList<>();
            for (String tag : record.tags) {
                if (isPlaybackStateTag(tag)) pendingState.add(tag);
                else segmentTags.add(tag);
            }
            if (!retained.contains(record)) {
                needsTimeAnchor = !Double.isNaN(nextTime);
                time = nextTime;
                continue;
            }
            List<String> tags = new ArrayList<>(pendingState);
            if (needsTimeAnchor && isEmpty(anchor) && !Double.isNaN(time)) {
                java.text.SimpleDateFormat format = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
                format.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
                tags.add("#EXT-X-PROGRAM-DATE-TIME:" + format.format(new java.util.Date(Math.round(time))));
            }
            tags.addAll(segmentTags);
            time = nextTime;
            needsTimeAnchor = false;
            pendingState.clear();
            restored.add(new Record(true, record.line, record.host, record.resolvedUri, tags,
                    record.duration, record.adLikeUri, record.explicitAd, record.weakAdNumberUri));
        }
        return restored;
    }

    private static boolean isPlaybackStateTag(String tag) {
        return tag.startsWith("#EXT-X-KEY:") || tag.startsWith("#EXT-X-MAP:")
                || tag.equals("#EXT-X-DISCONTINUITY");
    }

    private static String build(List<Record> records, int capacity) {
        StringBuilder sb = new StringBuilder(capacity);

        for (Record record : records) appendRecord(sb, record);

        if (sb.length() > 0) sb.setLength(sb.length() - 1);
        return sb.toString();
    }

    private static void appendRecord(StringBuilder sb, Record record) {
        if (!record.segment) {
            sb.append(record.line).append('\n');
        } else if (isPartOnlyRecord(record)) {
            for (String tag : record.tags) if (!isEmpty(tag)) sb.append(tag).append('\n');
        } else {
            for (String tag : record.tags) sb.append(tag).append('\n');
            sb.append(record.line).append('\n');
        }
    }

    private static boolean isPartOnlyRecord(Record record) {
        if (record == null || record.tags == null || record.tags.isEmpty()) return false;
        boolean hasPart = false;
        for (String tag : record.tags) {
            if (tag.startsWith("#EXTINF")) return false;
            if (tag.startsWith("#EXT-X-PART:")) hasPart = true;
        }
        return hasPart;
    }

    private static String resolveHost(String baseUrl, String uri) {
        try {
            String resolved = resolveUri(baseUrl, uri);
            return resolveHostFromResolved(resolved);
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static String resolveHostFromResolved(String resolved) {
        try {
            String host = new URI(resolved).getHost();
            return host == null ? "" : host.toLowerCase(Locale.US);
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static class Record {

        private final boolean segment;
        private final String line;
        private final String host;
        private final String resolvedUri;
        private final List<String> tags;
        private final double duration;
        private final boolean adLikeUri;
        private final boolean explicitAd;
        private final boolean weakAdNumberUri;

        private Record(boolean segment, String line, String host, String resolvedUri, List<String> tags, double duration,
                       boolean adLikeUri, boolean explicitAd, boolean weakAdNumberUri) {
            this.segment = segment;
            this.line = line;
            this.host = host;
            this.resolvedUri = resolvedUri;
            this.tags = tags;
            this.duration = duration;
            this.adLikeUri = adLikeUri;
            this.explicitAd = explicitAd;
            this.weakAdNumberUri = weakAdNumberUri;
        }

        public static Record plain(String line) {
            return new Record(false, line, "", "", null, 0, false, false, false);
        }

        public static Record segment(List<String> tags, String line, String host, String resolvedUri, double duration) {
            int uriAdSignals = classifySegmentUri(resolvedUri);
            return new Record(true, line, host, resolvedUri, tags, duration,
                    (uriAdSignals & URI_AD_LIKE) != 0,
                    isExplicitAdSegment(tags, uriAdSignals),
                    (uriAdSignals & URI_STRONG_AD) == 0 && containsAdNumberMediaPath(resolvedUri));
        }
    }

    private static class Pod {

        // Some current sources insert only one or two ad segments between
        // discontinuities; others use longer SSAI blocks. Widen candidate
        // collection, but keep deletion gated by the multi-signal score above.
        private static final int MIN_SEGMENTS = 1;
        private static final int MAX_SEGMENTS = 24;
        private static final double MAX_DURATION_SECONDS = 120;

        private final int start;
        private final int end;
        private final int segmentCount;
        private final String durationFingerprint;
        private final String mediaFingerprint;
        private final boolean strongAdSignal;
        private final boolean keySwitchSignal;
        private final boolean clearKeyExcursionSignal;
        private final double duration;

        private Pod(int start, int end, int segmentCount, String durationFingerprint, String mediaFingerprint,
                    boolean strongAdSignal, boolean keySwitchSignal, boolean clearKeyExcursionSignal, double duration) {
            this.start = start;
            this.end = end;
            this.segmentCount = segmentCount;
            this.durationFingerprint = durationFingerprint;
            this.mediaFingerprint = mediaFingerprint;
            this.strongAdSignal = strongAdSignal;
            this.keySwitchSignal = keySwitchSignal;
            this.clearKeyExcursionSignal = clearKeyExcursionSignal;
            this.duration = duration;
        }

        private static Pod create(List<Record> records, int start, int end, String beforeKey, String afterKey) {
            int segments = 0;
            int lastSegment = -1;
            double duration = 0;
            boolean strongAdSignal = false;
            String podKey = "";
            StringBuilder durationFingerprint = new StringBuilder();
            StringBuilder mediaFingerprint = new StringBuilder();

            for (int i = start; i < end; i++) {
                Record record = records.get(i);
                if (!record.segment) continue;
                // Reusing one resource with different/implicit ranges is normal HLS,
                // including across discontinuities; URI equality is not repetition.
                if (hasTagPrefix(record.tags, "#EXT-X-BYTERANGE")
                        || (isPartOnlyRecord(record) && !isEmpty(parseAttributeString(
                        lastTag(record.tags, "#EXT-X-PART:"), "BYTERANGE")))) return null;
                if (++segments > MAX_SEGMENTS) return null;
                lastSegment = i;
                duration += record.duration;
                strongAdSignal |= record.explicitAd;
                String key = lastKey(record.tags);
                if (!isEmpty(key)) podKey = key;
                durationFingerprint.append(Math.round(record.duration * 10)).append(',');
                mediaFingerprint.append(exactMediaIdentity(record.resolvedUri)).append('\n');
            }

            if (segments < MIN_SEGMENTS || segments > MAX_SEGMENTS || duration <= 0 || duration > MAX_DURATION_SECONDS) return null;
            boolean keySwitchSignal = hasKeySwitchSignal(beforeKey, podKey, afterKey);
            boolean clearKeyExcursionSignal = hasClearKeyExcursionSignal(beforeKey, podKey, afterKey);
            return new Pod(start, lastSegment + 1, segments, segments + ":" + durationFingerprint,
                    segments + ":" + mediaFingerprint, strongAdSignal, keySwitchSignal, clearKeyExcursionSignal, duration);
        }

        private static boolean hasKeySwitchSignal(String beforeKey, String podKey, String afterKey) {
            if (isEmpty(podKey)) return false;
            if (podKey.equals(beforeKey)) return false;
            return !isEmpty(beforeKey) && afterKey.equals(beforeKey);
        }

        private static boolean hasClearKeyExcursionSignal(String beforeKey, String podKey, String afterKey) {
            String beforeMethod = keyMethod(beforeKey);
            String podMethod = keyMethod(podKey);
            String afterMethod = keyMethod(afterKey);
            if (!"NONE".equals(podMethod) || isEmpty(beforeMethod) || isEmpty(afterMethod)) return false;
            return !"NONE".equals(beforeMethod) && beforeMethod.equals(afterMethod);
        }

        private static String keyMethod(String keyLine) {
            if (isEmpty(keyLine)) return "";
            return parseAttributeString(keyLine, "METHOD").toUpperCase(Locale.US);
        }

        private static String lastKey(List<String> tags) {
            if (tags == null || tags.isEmpty()) return "";
            String key = "";
            for (String tag : tags) {
                if (tag != null && tag.startsWith("#EXT-X-KEY:")) key = tag;
            }
            return key;
        }
    }

}
