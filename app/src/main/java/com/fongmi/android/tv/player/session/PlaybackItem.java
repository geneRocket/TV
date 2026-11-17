package com.fongmi.android.tv.player.session;

import com.fongmi.android.tv.bean.Drm;
import com.fongmi.android.tv.bean.Sub;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class PlaybackItem {

    private final String identity;
    private final String url;
    private final Map<String, String> headers;
    private final String format;
    private final Drm drm;
    private final List<Sub> subtitles;
    private final long startPosition;
    private final boolean forceLive;
    private final boolean playWhenReady;

    public PlaybackItem(String identity, String url, Map<String, String> headers, String format, Drm drm, List<Sub> subtitles, long startPosition, boolean forceLive) {
        this(identity, url, headers, format, drm, subtitles, startPosition, forceLive, true);
    }

    public PlaybackItem(String identity, String url, Map<String, String> headers, String format, Drm drm, List<Sub> subtitles, long startPosition, boolean forceLive, boolean playWhenReady) {
        if (identity == null || identity.isEmpty()) throw new IllegalArgumentException("identity must not be empty");
        if (url == null || url.trim().isEmpty()) throw new IllegalArgumentException("url must not be empty");
        this.identity = identity;
        this.url = url;
        this.headers = Collections.unmodifiableMap(new LinkedHashMap<>(headers == null ? Collections.emptyMap() : headers));
        this.format = format;
        this.drm = drm;
        this.subtitles = Collections.unmodifiableList(nonNullSubtitles(subtitles));
        this.startPosition = startPosition == androidx.media3.common.C.TIME_UNSET
                ? startPosition : Math.max(startPosition, 0);
        this.forceLive = forceLive;
        this.playWhenReady = playWhenReady;
    }

    public String getIdentity() { return identity; }

    public String getUrl() { return url; }

    public Map<String, String> getHeaders() { return headers; }

    public String getFormat() { return format; }

    public Drm getDrm() { return drm; }

    public List<Sub> getSubtitles() { return subtitles; }

    public long getStartPosition() { return startPosition; }

    public boolean isForceLive() { return forceLive; }

    public boolean isPlayWhenReady() { return playWhenReady; }

    public PlaybackItem withFormat(String value) {
        return new PlaybackItem(identity, url, headers, value, drm, subtitles, startPosition, forceLive, playWhenReady);
    }

    public PlaybackItem withPlayWhenReady(boolean value) {
        return new PlaybackItem(identity, url, headers, format, drm, subtitles, startPosition, forceLive, value);
    }

    private static List<Sub> nonNullSubtitles(List<Sub> subtitles) {
        if (subtitles == null || subtitles.isEmpty()) return Collections.emptyList();
        List<Sub> result = new ArrayList<>(subtitles.size());
        for (Sub subtitle : subtitles) if (subtitle != null) result.add(subtitle);
        return result;
    }
}
