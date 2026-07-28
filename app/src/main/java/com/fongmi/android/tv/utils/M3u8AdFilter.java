package com.fongmi.android.tv.utils;

import com.fongmi.android.tv.api.config.LiveConfig;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.Rule;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

public final class M3u8AdFilter {

    private static final int SUBTITLE_WHITELIST_MAX = 512;
    private static final int MAX_CONTIGUOUS_AD_SEGMENTS = 240;
    private static final int MAX_UNBOUNDED_CUE_AD_SEGMENTS = 20;
    private static final int MAX_CACHED_PATTERNS = 256;
    private static final Map<String, Pattern> PATTERN_CACHE = new ConcurrentHashMap<>();

    private static final Set<String> SUBTITLE_PLAYLIST_WHITELIST = Collections.newSetFromMap(new LinkedHashMap<String, Boolean>(SUBTITLE_WHITELIST_MAX, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
            return size() > SUBTITLE_WHITELIST_MAX;
        }
    });

    private static final Set<String> AD_KEYWORDS = new HashSet<String>() {{
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
    }};

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

        if (isSubtitleWhitelisted(baseUrl)) return toBytesOrOriginal(content, bytes);

        content = filterConfiguredRules(content, baseUrl);
        registerSubtitlePlaylists(content, baseUrl);
        content = filterAdVariantPlaylists(content, baseUrl);

        if (!content.contains("#EXTINF") && !content.contains("#EXT-X-PART")) {
            return toBytesOrOriginal(content, bytes);
        }

        List<Record> records = parseRecords(content, baseUrl);
        if (isSubtitlePlaylist(content, records)) return toBytesOrOriginal(content, bytes);

        records = filterRepeatedDiscontinuityPods(records);
        records = filterAdTagSegments(records, baseUrl);
        records = filterShortAdClusters(records);

        String filtered = filterMinorHostSegments(records, content);
        // Never hand Exo a playlist that the filter has emptied. A false-positive ad rule
        // should leave the original stream playable rather than turn into a playback error.
        if (!startsWithM3uHeader(filtered) || countSegmentsFromString(filtered) == 0) return toBytesOrOriginal(content, bytes);

        return toBytesOrOriginal(filtered, bytes);
    }

    private static String filterConfiguredRules(String content, String baseUrl) {
        if (isEmpty(baseUrl)) return content;

        try {
            String host = UrlUtil.host(UrlUtil.uri(baseUrl));
            if (isEmpty(host)) return content;

            return filterConfiguredRules(content, host, VodConfig.get().getRules(), LiveConfig.get().getRules());
        } catch (Throwable ignored) {
            return content;
        }
    }

    private static String filterConfiguredRules(String content, String host, List<Rule> vodRules, List<Rule> liveRules) {
        String filtered = filterConfiguredRules(content, host, vodRules);
        return filterConfiguredRules(filtered, host, liveRules);
    }

    private static String filterConfiguredRules(String content, String host, List<Rule> rules) {
        String filtered = content;
        for (Rule rule : rules) if (matchesRuleHost(host, rule.getHosts())) filtered = filterByRegexRules(filtered, rule.getRegex());
        return filtered;
    }

    private static boolean matchesRuleHost(String host, List<String> hosts) {
        String value = host.toLowerCase(Locale.US);
        for (String item : hosts) {
            if (item == null || item.isEmpty()) continue;
            String rule = item.toLowerCase(Locale.US).trim();
            if (value.contains(rule)) return true;
            if (!rule.contains("*")) continue;
            if (getPattern("glob:" + rule, Pattern.quote(rule).replace("*", "\\E.*\\Q")).matcher(value).find()) return true;
        }
        return false;
    }

    static String filterByRegexRules(String content, List<String> regexes) {
        if (content == null || content.isEmpty() || regexes == null || regexes.isEmpty()) return content;

        String filtered = content;
        int segmentCount = countSegmentsFromString(content);
        if (segmentCount <= 0) return content;

        for (String regex : regexes) {
            if (!isM3u8AdRegex(regex)) continue;

            try {
                String candidate = getPattern("regex:" + regex, regex).matcher(filtered).replaceAll("");
                int candidateSegments = countSegmentsFromString(candidate);
                if (candidateSegments <= 0 || candidateSegments >= segmentCount || !startsWithM3uHeader(candidate)) continue;
                filtered = candidate;
                segmentCount = candidateSegments;
            } catch (Throwable ignored) {
            }
        }

        return filtered;
    }

    private static boolean isM3u8AdRegex(String regex) {
        if (regex == null || regex.isEmpty()) return false;
        String lower = regex.toLowerCase(Locale.US);
        return lower.contains("#ext")
                || lower.contains(".ts")
                || lower.contains(".m4s")
                || lower.contains(".mp4");
    }

    private static Pattern getPattern(String key, String expression) {
        Pattern pattern = PATTERN_CACHE.get(key);
        if (pattern != null) return pattern;
        if (PATTERN_CACHE.size() >= MAX_CACHED_PATTERNS) PATTERN_CACHE.clear();
        pattern = Pattern.compile(expression);
        Pattern previous = PATTERN_CACHE.putIfAbsent(key, pattern);
        return previous == null ? pattern : previous;
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

        if (lowerContent.startsWith("webvtt") || lowerContent.contains("\nwebvtt")) return true;
        if (lowerContent.contains("type=subtitles")) return true;
        if (lowerContent.contains("type=closed-captions")) return true;
        if (lowerContent.contains("subtitle") || lowerContent.contains("subtitles")) return true;
        if (lowerContent.contains("caption") || lowerContent.contains("captions")) return true;

        int subtitleUriCount = 0;
        int segmentCount = 0;

        for (Record record : records) {
            if (record.segment) segmentCount++;
            if (isSubtitleUri(record.line)) subtitleUriCount++;
            if (record.tags == null) continue;
            for (String tag : record.tags) {
                if (isSubtitleMapTag(tag)) subtitleUriCount++;
                if (tag.toLowerCase(Locale.US).contains("webvtt")) return true;
            }
        }

        return segmentCount > 0 && subtitleUriCount >= Math.max(1, segmentCount / 2);
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

        for (int i = 0; i < lines.length; i++) {
            String line = trimLineEnd(lines[i]).trim();

            if (line.startsWith("#EXT-X-I-FRAME-STREAM-INF")) {
                variants++;
                String uri = parseAttributeString(line, "URI");
                if (isLikelyAdPlaylistUri(uri, baseUrl) || containsStrongAdSignal(line)) {
                    remove[i] = true;
                    adVariants++;
                }
                continue;
            }

            if (line.startsWith("#EXT-X-MEDIA:")) {
                String type = parseAttributeString(line, "TYPE");
                String uri = parseAttributeString(line, "URI");

                if ("SUBTITLES".equalsIgnoreCase(type) || "CLOSED-CAPTIONS".equalsIgnoreCase(type)) {
                    continue;
                }

                String groupKey = mediaGroupKey(type, parseAttributeString(line, "GROUP-ID"));
                boolean hasAlternative = mediaGroupCounts.getOrDefault(groupKey, 0) > 1;
                if (hasAlternative && !isEmpty(uri) && (isLikelyAdPlaylistUri(uri, baseUrl) || containsStrongAdSignal(line))) {
                    remove[i] = true;
                }
                continue;
            }

            if (!line.startsWith("#EXT-X-STREAM-INF")) continue;

            int uriIndex = findNextUriLine(lines, i + 1);
            if (uriIndex < 0) continue;

            variants++;
            String uri = trimLineEnd(lines[uriIndex]).trim();

            if (isLikelyAdPlaylistUri(uri, baseUrl) || containsStrongAdSignal(line) || containsStrongAdSignal(uri)) {
                remove[i] = true;
                remove[uriIndex] = true;
                adVariants++;
            }
        }

        boolean changed = false;
        for (boolean value : remove) changed |= value;
        if (!changed) return content;
        if (variants > 0 && adVariants >= variants) return content;

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
        return isLikelyPlaylistResource(resolved) && isAdLikeUri(resolved);
    }

    private static boolean isLikelyPlaylistResource(String uri) {
        String path = stripQueryAndFragment(uri);
        return path.endsWith(".m3u8") || path.endsWith(".m3u");
    }

    private static String filterMinorHostSegments(List<Record> records, String original) {
        Map<String, Integer> hostCount = new HashMap<>();
        int segmentCount = 0;

        for (Record record : records) {
            if (!record.segment) continue;
            segmentCount++;
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

        if (majorHost == null) return build(records, original.length());

        boolean hasStableMajorHost = (majorCount * 10) >= (segmentCount * 5);
        if (!hasStableMajorHost) return build(records, original.length());

        StringBuilder sb = new StringBuilder(original.length());
        int removed = 0;

        for (Record record : records) {
            if (!record.segment) {
                appendRecord(sb, record);
                continue;
            }

            String h = record.host == null ? "" : record.host;
            boolean sameAsMajor = isEmpty(h) || majorHost.equals(h);
            boolean explicitAd = isExplicitAdRecord(record);
            boolean minorAdHost = !sameAsMajor && isAdLikeUri(record.resolvedUri);
            boolean minorHost = !sameAsMajor && hostCount.getOrDefault(h, 0) <= Math.max(1, segmentCount / 5);

            if (explicitAd || minorAdHost || minorHost) {
                removed++;
                continue;
            }

            appendRecord(sb, record);
        }

        if (removed <= 0) return build(records, original.length());
        if (countSegmentsFromString(sb.toString()) <= 0) return build(records, original.length());

        if (sb.length() > 0) sb.setLength(sb.length() - 1);
        return sb.toString();
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

    private static List<Record> filterAdTagSegments(List<Record> records, String baseUrl) {
        List<Record> kept = new ArrayList<>(records.size());

        boolean cueAdOpen = false;
        double cueAdSeconds = 0;
        int cueAdSegments = 0;

        double dateRangeAdSeconds = 0;

        boolean discontinuityAdOpen = false;
        int discontinuityAdSegments = 0;

        boolean pendingAdSignal = false;
        int pendingAdSignalLines = 0;

        for (Record record : records) {
            if (!record.segment) {
                if (isCueOutTag(record.line)) {
                    cueAdOpen = true;
                    cueAdSeconds = Math.max(cueAdSeconds, parseCueOutDuration(record.line));
                    cueAdSegments = 0;
                    continue;
                }

                if (isCueInTag(record.line)) {
                    cueAdOpen = false;
                    cueAdSeconds = 0;
                    cueAdSegments = 0;
                    dateRangeAdSeconds = 0;
                    discontinuityAdOpen = false;
                    discontinuityAdSegments = 0;
                    pendingAdSignal = false;
                    pendingAdSignalLines = 0;
                    continue;
                }

                if (isAdDateRangeTag(record.line)) {
                    double duration = parseDateRangeDuration(record.line);
                    if (duration > 0) dateRangeAdSeconds = Math.max(dateRangeAdSeconds, duration);
                    pendingAdSignal = true;
                    pendingAdSignalLines = 0;
                    continue;
                }

                if (isAdSignalLine(record.line)) {
                    pendingAdSignal = true;
                    pendingAdSignalLines = 0;
                    continue;
                }

                if (record.line.startsWith("#EXT-X-CUE-")) continue;
                if (record.line.startsWith("#EXT-OATCLS-SCTE35")) continue;
                if (record.line.startsWith("#EXT-X-SCTE35")) continue;
                if (record.line.startsWith("#EXT-X-SPLICEPOINT-SCTE35")) continue;
                if (record.line.startsWith("#EXT-X-ASSET")) continue;

                if (isStandaloneAdResourceTag(record.line, baseUrl)) continue;

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

            boolean explicitAd = isExplicitAdRecord(record);
            boolean adByPendingSignal = pendingAdSignal && (explicitAd || isAdLikeUri(record.resolvedUri) || hasAdSignalTag(record.tags));
            boolean hasDiscontinuity = hasTagPrefix(record.tags, "#EXT-X-DISCONTINUITY");

            if (explicitAd && hasDiscontinuity) {
                discontinuityAdOpen = true;
                discontinuityAdSegments = 0;
            }

            if (discontinuityAdOpen) {
                if (!explicitAd && discontinuityAdSegments > 0 && hasDiscontinuity) {
                    discontinuityAdOpen = false;
                    discontinuityAdSegments = 0;
                } else {
                    discontinuityAdSegments++;
                    if (discontinuityAdSegments <= MAX_CONTIGUOUS_AD_SEGMENTS) continue;
                    discontinuityAdOpen = false;
                    discontinuityAdSegments = 0;
                }
            }

            if (explicitAd || adByPendingSignal) {
                pendingAdSignal = false;
                pendingAdSignalLines = 0;
                continue;
            }

            if (cueAdOpen || dateRangeAdSeconds > 0) {
                if (cueAdOpen && shouldEndCue(cueAdSeconds, cueAdSegments)) {
                    cueAdOpen = false;
                    cueAdSegments = 0;
                }

                if (!cueAdOpen && dateRangeAdSeconds <= 0) {
                    kept.add(record);
                    continue;
                }

                if (cueAdOpen) cueAdSegments++;
                if (cueAdOpen && cueAdSeconds > 0) {
                    cueAdSeconds = nextRemaining(cueAdSeconds, record.duration);
                    if (cueAdSeconds <= 0) cueAdOpen = false;
                }

                if (dateRangeAdSeconds > 0) {
                    dateRangeAdSeconds = nextRemaining(dateRangeAdSeconds, record.duration);
                }

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

    private static List<Record> filterRepeatedDiscontinuityPods(List<Record> records) {
        if (records == null || records.isEmpty()) return records;

        List<Pod> pods = new ArrayList<>();
        int podStart = -1;
        for (int i = 0; i < records.size(); i++) {
            Record record = records.get(i);
            if (!record.segment || !hasTagPrefix(record.tags, "#EXT-X-DISCONTINUITY")) continue;
            if (podStart >= 0) addPod(pods, records, podStart, i);
            podStart = i;
        }
        if (podStart >= 0) addPod(pods, records, podStart, records.size());

        Map<String, Integer> counts = new HashMap<>();
        for (Pod pod : pods) counts.put(pod.fingerprint, counts.getOrDefault(pod.fingerprint, 0) + 1);

        boolean[] remove = new boolean[records.size()];
        int removedSegments = 0;
        for (Pod pod : pods) {
            if (!shouldRemoveRepeatedPod(counts.getOrDefault(pod.fingerprint, 0), pod.strongAdSignal)) continue;
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

    private static void addPod(List<Pod> pods, List<Record> records, int start, int end) {
        Pod pod = Pod.create(records, start, end);
        if (pod != null) pods.add(pod);
    }

    static boolean shouldEndCue(double declaredSeconds, int removedSegments) {
        int limit = declaredSeconds <= 0 ? MAX_UNBOUNDED_CUE_AD_SEGMENTS : MAX_CONTIGUOUS_AD_SEGMENTS;
        return removedSegments >= limit;
    }

    static boolean shouldRemoveRepeatedPod(int matchingPods, boolean strongAdSignal) {
        return strongAdSignal && matchingPods >= 2;
    }

    private static List<Record> filterShortAdClusters(List<Record> records) {
        if (records == null || records.isEmpty()) return records;

        List<Record> kept = new ArrayList<>(records.size());
        List<Record> cluster = new ArrayList<>();

        int segmentCount = countSegments(records);
        if (segmentCount < 4) return records;

        for (Record record : records) {
            if (!record.segment) {
                flushCluster(cluster, kept, segmentCount);
                kept.add(record);
                continue;
            }

            if (isExplicitAdRecord(record) || isAdLikeUri(record.resolvedUri)) {
                cluster.add(record);
            } else {
                flushCluster(cluster, kept, segmentCount);
                kept.add(record);
            }
        }

        flushCluster(cluster, kept, segmentCount);

        if (countSegments(kept) <= 0) return records;
        return kept;
    }

    private static void flushCluster(List<Record> cluster, List<Record> kept, int segmentCount) {
        if (cluster.isEmpty()) return;

        int clusterSize = cluster.size();
        double duration = 0;
        for (Record record : cluster) duration += record.duration;

        boolean hasExplicitAd = hasAnyExplicitAd(cluster);
        boolean looksLikeAdCluster = clusterSize <= Math.max(1, segmentCount / 3)
                && (hasExplicitAd || (clusterSize >= 2 && duration <= 120));

        if (!looksLikeAdCluster) kept.addAll(cluster);

        cluster.clear();
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
                && !lower.startsWith("#ext-x-rendition-report:")) return false;

        String uri = parseAttributeString(line, "URI");
        if (isEmpty(uri)) return false;

        return isLikelyAdSegmentUri(resolveUri(baseUrl, uri));
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
                records.add(Record.segment(tags, uri, resolveHostFromResolved(resolved), resolved, parsePartDuration(line)));
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
                    records.add(Record.segment(new ArrayList<>(pending), line, resolveHostFromResolved(resolved), resolved, parseExtInfDuration(pending)));
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

    private static boolean isSegmentTag(String line) {
        return line.startsWith("#EXTINF")
                || line.startsWith("#EXT-X-DISCONTINUITY")
                || line.startsWith("#EXT-X-PROGRAM-DATE-TIME")
                || line.startsWith("#EXT-X-BYTERANGE")
                || line.startsWith("#EXT-X-GAP");
    }

    private static boolean isSegmentFollowTag(String line) {
        return line.startsWith("#EXT-X-BYTERANGE")
                || line.startsWith("#EXT-X-GAP")
                || line.startsWith("#EXT-X-PROGRAM-DATE-TIME")
                || line.startsWith("#EXT-X-MAP:");
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
                || lower.startsWith("#ext-x-ad-start")
                || lower.startsWith("#ext-x-vmap-ad-break");
    }

    private static boolean isCueInTag(String line) {
        String lower = line.toLowerCase(Locale.US);
        return lower.startsWith("#ext-x-cue-in")
                || lower.startsWith("#ext-cue-in")
                || lower.startsWith("#ext-x-ad-end");
    }

    private static boolean isAdDateRangeTag(String line) {
        if (!line.startsWith("#EXT-X-DATERANGE")) return false;

        String lower = line.toLowerCase(Locale.US);
        if (lower.contains("scte35")) return true;
        if (lower.contains("scte-35")) return true;
        if (lower.contains("cue")) return true;

        String id = parseAttributeString(line, "ID");
        String clazz = parseAttributeString(line, "CLASS");
        String type = parseAttributeString(line, "TYPE");
        String asset = parseAttributeString(line, "X-ASSET");
        String scte = parseAttributeString(line, "SCTE35-OUT");

        return containsAdKeyword(id)
                || containsAdKeyword(clazz)
                || containsAdKeyword(type)
                || containsAdKeyword(asset)
                || !isEmpty(scte);
    }

    private static boolean isAdSignalLine(String line) {
        if (isEmpty(line)) return false;

        String lower = line.toLowerCase(Locale.US);

        if (lower.startsWith("#ext-oatcls-scte35")) return true;
        if (lower.startsWith("#ext-x-scte35")) return true;
        if (lower.startsWith("#ext-x-splicepoint-scte35")) return true;
        if (lower.startsWith("#ext-x-asset")) return true;
        if (lower.startsWith("#ext-x-ad")) return true;
        if (lower.startsWith("#ext-x-vast")) return true;
        if (lower.startsWith("#ext-x-vmap")) return true;

        return lower.startsWith("#") && containsStrongAdSignal(lower);
    }

    private static double parseCueOutDuration(String line) {
        int index = line.indexOf(':');
        if (index > -1 && index < line.length() - 1) {
            String part = line.substring(index + 1).trim();
            if (!part.contains("=")) return parseDouble(part);
        }

        double duration = parseAttributeDouble(line, "DURATION");
        if (duration > 0) return duration;

        duration = parseAttributeDouble(line, "PLANNED-DURATION");
        if (duration > 0) return duration;

        duration = parseAttributeDouble(line, "ElapsedTime");
        if (duration > 0) return duration;

        duration = parseAttributeDouble(line, "TOTAL-DURATION");
        if (duration > 0) return duration;

        return 0;
    }

    private static double parseDateRangeDuration(String line) {
        double duration = parseAttributeDouble(line, "DURATION");
        if (duration > 0) return duration;

        duration = parseAttributeDouble(line, "PLANNED-DURATION");
        if (duration > 0) return duration;

        duration = parseAttributeDouble(line, "X-DURATION");
        if (duration > 0) return duration;

        return 0;
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

        String lowerLine = line.toLowerCase(Locale.US);
        String lowerToken = token.toLowerCase(Locale.US);
        idx = lowerLine.indexOf(lowerToken);

        while (idx >= 0) {
            if (idx == 0 || lowerLine.charAt(idx - 1) == ',' || lowerLine.charAt(idx - 1) == ':' || lowerLine.charAt(idx - 1) == ' ') {
                return idx;
            }
            idx = lowerLine.indexOf(lowerToken, idx + 1);
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
            if (AD_KEYWORDS.contains(token)) return true;

            if (token.startsWith("ad") && token.length() <= 12) {
                if (token.equals("ad") || token.equals("ads") || token.equals("adv")) return true;
                if (token.contains("roll") || token.contains("pod") || token.contains("break")) return true;
            }

            if (hasEndingAdWord(token)) return true;
            if (token.contains("scte35")) return true;
            if (token.contains("vast")) return true;
            if (token.contains("vmap")) return true;
        }

        return false;
    }

    private static boolean containsStrongAdSignal(String text) {
        if (isEmpty(text)) return false;

        String lower = text.toLowerCase(Locale.US);

        if (lower.contains("scte35")) return true;
        if (lower.contains("scte-35")) return true;
        if (lower.contains("cue-out")) return true;
        if (lower.contains("cue_out")) return true;
        if (lower.contains("ad-break")) return true;
        if (lower.contains("ad_break")) return true;
        if (lower.contains("adbreak")) return true;
        if (lower.contains("ad-pod")) return true;
        if (lower.contains("ad_pod")) return true;
        if (lower.contains("adpod")) return true;
        if (lower.contains("preroll")) return true;
        if (lower.contains("midroll")) return true;
        if (lower.contains("postroll")) return true;
        if (lower.contains("doubleclick")) return true;
        if (lower.contains("gampad")) return true;
        if (lower.contains("freewheel")) return true;
        if (lower.contains("spotx")) return true;

        return containsAdKeyword(lower);
    }

    private static boolean isExplicitAdRecord(Record record) {
        if (record == null || !record.segment) return false;
        if (hasExplicitAdTag(record.tags)) return true;
        if (isStrongAdSegmentUri(record.resolvedUri) || isStrongAdSegmentUri(record.line)) return true;

        boolean hasDiscontinuity = hasTagPrefix(record.tags, "#EXT-X-DISCONTINUITY");
        return hasDiscontinuity
                && (isWeakAdNumberSegmentUri(record.resolvedUri) || isWeakAdNumberSegmentUri(record.line));
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
        if (lower.startsWith("#ext-oatcls-scte35")) return true;
        if (lower.startsWith("#ext-x-scte35")) return true;
        if (lower.startsWith("#ext-x-splicepoint-scte35")) return true;
        if (lower.startsWith("#ext-x-asset")) return true;
        if (lower.startsWith("#ext-x-ad")) return true;
        if (lower.startsWith("#ext-x-vast")) return true;
        if (lower.startsWith("#ext-x-vmap")) return true;

        if (lower.startsWith("#ext-x-daterange")) return isAdDateRangeTag(tag);

        return lower.startsWith("#") && containsStrongAdSignal(lower);
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

    private static boolean isLikelyAdSegmentUri(String uri) {
        if (isEmpty(uri)) return false;

        String lower = uri.toLowerCase(Locale.US).trim();
        int fragment = lower.indexOf('#');
        if (fragment >= 0) lower = lower.substring(0, fragment);

        if (!isLikelySegmentResource(lower)) return false;

        return isAdLikeUri(lower);
    }

    private static boolean isStrongAdSegmentUri(String uri) {
        if (isEmpty(uri)) return false;

        String lower = uri.toLowerCase(Locale.US).trim();
        int fragment = lower.indexOf('#');
        if (fragment >= 0) lower = lower.substring(0, fragment);

        if (!isLikelySegmentResource(lower)) return false;
        if (containsAdJumpMediaPath(lower)) return true;
        return isStrongAdLikeUri(lower);
    }

    private static boolean isWeakAdNumberSegmentUri(String uri) {
        if (isEmpty(uri) || !isLikelySegmentResource(uri)) return false;
        if (isStrongAdSegmentUri(uri)) return false;

        String path = stripQueryAndFragment(uri).toLowerCase(Locale.US);
        for (String part : path.split("[/._\\-]+")) {
            if (isAdNumber(part, "ad") || isAdNumber(part, "ads")) return true;
        }
        return false;
    }

    private static boolean containsAdJumpMediaPath(String uri) {
        if (isEmpty(uri) || !isLikelySegmentResource(uri)) return false;
        String path = stripQueryAndFragment(uri).toLowerCase(Locale.US);
        for (String part : path.split("[/._\\-]+")) {
            if ("adjump".equals(part)) return true;
        }
        return false;
    }

    private static boolean isStrongAdLikeUri(String uri) {
        if (isEmpty(uri)) return false;

        String lower = uri.toLowerCase(Locale.US).trim();
        if (containsAdQueryKey(lower)) return true;
        if (containsAdKeyword(lower)) return true;
        if (containsAdPathPart(lower, false)) return true;
        try {
            if (AdBlocker.isAdUrl(lower)) return true;
        } catch (RuntimeException ignored) {
            // Keep playlist filtering available when the optional URL blocker cannot parse a URI.
        }
        return false;
    }

    private static boolean isAdLikeUri(String uri) {
        if (isEmpty(uri)) return false;

        String lower = uri.toLowerCase(Locale.US).trim();

        if (containsAdQueryKey(lower)) return true;
        if (containsAdKeyword(lower)) return true;
        if (containsAdPathPart(lower)) return true;
        try {
            if (AdBlocker.isAdUrl(lower)) return true;
        } catch (RuntimeException ignored) {
            // Keep playlist filtering available when the optional URL blocker cannot parse a URI.
        }

        return false;
    }

    static boolean containsAdQueryKey(String uri) {
        int query = uri.indexOf('?');
        if (query < 0 || query >= uri.length() - 1) return false;
        int fragment = uri.indexOf('#', query + 1);
        int end = fragment >= 0 ? fragment : uri.length();

        for (int start = query + 1; start < end; ) {
            int separator = uri.indexOf('&', start);
            if (separator < 0 || separator > end) separator = end;
            int equals = uri.indexOf('=', start);
            if (equals < 0 || equals > separator) equals = separator;

            String key = safeDecode(uri.substring(start, equals)).toLowerCase(Locale.US);

            if (AD_QUERY_KEYS.contains(key)) return true;
            if (containsAdKeyword(key)) return true;

            if (isAdValueKey(key) && equals < separator) {
                String value = safeDecode(uri.substring(equals + 1, separator)).toLowerCase(Locale.US);
                if (containsAdKeyword(value)) return true;
            }
            start = separator + 1;
        }

        return false;
    }

    private static boolean isAdValueKey(String key) {
        return "type".equals(key)
                || "role".equals(key)
                || "class".equals(key)
                || "category".equals(key)
                || "asset".equals(key)
                || "content".equals(key)
                || "label".equals(key);
    }

    private static boolean isEmpty(CharSequence value) {
        return value == null || value.length() == 0;
    }

    private static boolean containsAdPathPart(String uri) {
        return containsAdPathPart(uri, true);
    }

    private static boolean containsAdPathPart(String uri, boolean includeAdNumbers) {
        String path = stripQueryAndFragment(uri);
        if (isEmpty(path)) return false;

        String[] parts = path.toLowerCase(Locale.US).split("[/._\\-]+");
        for (String part : parts) {
            if (isEmpty(part)) continue;
            if (AD_PATH_PARTS.contains(part)) return true;
            if (part.startsWith("ad") && part.length() <= 16
                    && (includeAdNumbers ? hasAdBoundaryWord(part) : hasStrongAdBoundaryWord(part))) return true;
            if (hasEndingAdWord(part)) return true;
            if (part.contains("adbreak") || part.contains("adpod")) return true;
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
                || value.startsWith("adbreak");
    }

    private static boolean isAdNumber(String value, String prefix) {
        return value.length() > prefix.length() && value.startsWith(prefix) && Character.isDigit(value.charAt(prefix.length()));
    }

    private static boolean hasEndingAdWord(String value) {
        return value.equals("videoad") || value.equals("playad") || value.equals("preloadad");
    }

    private static String safeDecode(String value) {
        if (value == null) return "";
        try {
            return URLDecoder.decode(value, StandardCharsets.UTF_8.name());
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

    private static double parseDouble(String value) {
        try {
            return Double.parseDouble(value.trim());
        } catch (Throwable ignored) {
            return 0;
        }
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

        private Record(boolean segment, String line, String host, String resolvedUri, List<String> tags, double duration) {
            this.segment = segment;
            this.line = line;
            this.host = host;
            this.resolvedUri = resolvedUri;
            this.tags = tags;
            this.duration = duration;
        }

        public static Record plain(String line) {
            return new Record(false, line, "", "", null, 0);
        }

        public static Record segment(List<String> tags, String line, String host, String resolvedUri, double duration) {
            return new Record(true, line, host, resolvedUri, tags, duration);
        }
    }

    private static class Pod {

        private static final int MIN_SEGMENTS = 3;
        private static final int MAX_SEGMENTS = 12;
        private static final double MAX_DURATION_SECONDS = 45;

        private final int start;
        private final int end;
        private final String fingerprint;
        private final boolean strongAdSignal;

        private Pod(int start, int end, String fingerprint, boolean strongAdSignal) {
            this.start = start;
            this.end = end;
            this.fingerprint = fingerprint;
            this.strongAdSignal = strongAdSignal;
        }

        private static Pod create(List<Record> records, int start, int end) {
            int segments = 0;
            int lastSegment = -1;
            double duration = 0;
            boolean strongAdSignal = false;
            StringBuilder fingerprint = new StringBuilder();

            for (int i = start; i < end; i++) {
                Record record = records.get(i);
                if (!record.segment) continue;
                segments++;
                lastSegment = i;
                duration += record.duration;
                strongAdSignal |= isExplicitAdRecord(record) || containsAdJumpMediaPath(record.resolvedUri);
                fingerprint.append(Math.round(record.duration * 10)).append(',');
            }

            if (segments < MIN_SEGMENTS || segments > MAX_SEGMENTS || duration <= 0 || duration > MAX_DURATION_SECONDS) return null;
            return new Pod(start, lastSegment + 1, segments + ":" + fingerprint, strongAdSignal);
        }
    }
}
